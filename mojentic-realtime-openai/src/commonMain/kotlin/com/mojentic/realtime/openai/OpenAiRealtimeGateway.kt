package com.mojentic.realtime.openai

import com.mojentic.errors.RealtimeGatewayException
import com.mojentic.realtime.ClientRealtimeEvent
import com.mojentic.realtime.RealtimeGateway
import com.mojentic.realtime.RealtimeGatewaySession
import com.mojentic.realtime.RealtimeVoiceConfig
import com.mojentic.realtime.openai.internal.OpenAiEventCodec
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.http.URLProtocol
import io.ktor.http.takeFrom
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val logger = KotlinLogging.logger("com.mojentic.realtime.openai.OpenAiRealtimeGateway")

/**
 * OpenAI Realtime gateway over Ktor WebSockets.
 *
 * The gateway is intentionally thin: it owns the WebSocket lifecycle and
 * JSON framing, sends initial `session.update` derived from the supplied
 * [RealtimeVoiceConfig], and surfaces raw server events as a [Flow]. All
 * orchestration (tool dispatch, event normalisation, audio plumbing) lives
 * in the broker layer above.
 */
public class OpenAiRealtimeGateway(
    private val apiKey: String,
    private val httpClient: HttpClient = defaultClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val betaHeader: String = DEFAULT_BETA_HEADER,
) : RealtimeGateway {

    override suspend fun open(
        model: String,
        config: RealtimeVoiceConfig,
        correlationId: String?,
    ): RealtimeGatewaySession {
        val session = try {
            httpClient.webSocketSession {
                url.takeFrom(baseUrl)
                url.parameters.append("model", model)
                if (url.protocol == URLProtocol.HTTP) url.protocol = URLProtocol.WS
                if (url.protocol == URLProtocol.HTTPS) url.protocol = URLProtocol.WSS
                header("Authorization", "Bearer $apiKey")
                header("OpenAI-Beta", betaHeader)
                correlationId?.let { header("X-Mojentic-Correlation-Id", it) }
            }
        } catch (failure: Throwable) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            throw RealtimeGatewayException("Failed to open OpenAI realtime WebSocket session", failure)
        }

        val wsSession = WebSocketRealtimeSession(session, json)
        wsSession.start()
        wsSession.send(ClientRealtimeEvent.SessionUpdate(config))
        return wsSession
    }

    public companion object {
        public const val DEFAULT_BASE_URL: String = "wss://api.openai.com/v1/realtime"
        public const val DEFAULT_BETA_HEADER: String = "realtime=v1"

        internal val json: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        public fun defaultClient(): HttpClient = HttpClient {
            install(WebSockets)
        }
    }
}

internal class WebSocketRealtimeSession(
    private val session: DefaultWebSocketSession,
    private val json: Json,
) : RealtimeGatewaySession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val incoming = Channel<JsonObject>(Channel.UNLIMITED)

    override val rawEvents: Flow<JsonObject> = incoming.consumeAsFlow()

    fun start() {
        scope.launch {
            try {
                session.incoming.consumeEach { frame ->
                    when (frame) {
                        is Frame.Text -> dispatchText(frame.readText())
                        is Frame.Binary -> { /* OpenAI realtime never sends binary frames currently */ }
                        is Frame.Close, is Frame.Ping, is Frame.Pong -> { /* handled by Ktor */ }
                        else -> { /* future Ktor Frame subtypes — ignore */ }
                    }
                }
            } catch (failure: Throwable) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                logger.warn(failure) { "WebSocket reader terminated abnormally" }
            }
        }
    }

    private suspend fun dispatchText(payload: String) {
        val parsed = try {
            json.parseToJsonElement(payload)
        } catch (failure: Throwable) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            logger.warn(failure) { "Failed to parse OpenAI realtime payload: ${payload.take(LOG_PREVIEW_CHARS)}" }
            return
        }
        val obj = parsed as? JsonObject ?: return
        incoming.send(obj)
    }

    private companion object {
        private const val LOG_PREVIEW_CHARS = 200
    }

    override suspend fun send(event: ClientRealtimeEvent) {
        val wire = OpenAiEventCodec.clientEventToWire(event)
        try {
            session.send(wire.toString())
        } catch (failure: Throwable) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            throw RealtimeGatewayException("Failed to send realtime client event ${event::class.simpleName}", failure)
        }
    }

    override suspend fun close() {
        try {
            session.close()
        } finally {
            incoming.close()
            scope.cancel()
        }
    }
}
