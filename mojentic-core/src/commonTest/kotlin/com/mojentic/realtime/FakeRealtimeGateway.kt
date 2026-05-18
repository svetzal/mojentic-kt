package com.mojentic.realtime

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * In-memory [RealtimeGateway] for tests.
 *
 * Records every [ClientRealtimeEvent] sent into a channel so tests can assert
 * over the client→server side of the conversation. Lets the test push raw
 * server events through [emit] to drive the session under test.
 */
internal class FakeRealtimeGateway : RealtimeGateway {
    val openedSessions: MutableList<FakeGatewaySession> = mutableListOf()
    var lastConfig: RealtimeVoiceConfig? = null
        private set
    var lastModel: String? = null
        private set

    override suspend fun open(
        model: String,
        config: RealtimeVoiceConfig,
        correlationId: String?,
    ): RealtimeGatewaySession {
        lastModel = model
        lastConfig = config
        val session = FakeGatewaySession()
        openedSessions += session
        return session
    }
}

internal class FakeGatewaySession : RealtimeGatewaySession {
    private val incoming: Channel<JsonObject> = Channel(Channel.UNLIMITED)
    val sent: Channel<ClientRealtimeEvent> = Channel(Channel.UNLIMITED)
    var closed: Boolean = false
        private set

    override val rawEvents: Flow<JsonObject> = incoming.consumeAsFlow()

    override suspend fun send(event: ClientRealtimeEvent) {
        sent.send(event)
    }

    override suspend fun close() {
        closed = true
        sent.close()
        incoming.close()
    }

    /** Push a raw provider event into the session's `rawEvents` flow. */
    suspend fun emit(payload: String) {
        val parsed = Json.parseToJsonElement(payload) as JsonObject
        incoming.send(parsed)
    }

    /** Convenience overload taking a [JsonObject] directly. */
    suspend fun emit(payload: JsonObject) {
        incoming.send(payload)
    }
}
