package com.mojentic.realtime

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * Vendor-neutral gateway for a realtime voice provider.
 *
 * Concrete implementations (e.g. `OpenAiRealtimeGateway`) own the duplex
 * transport — WebSocket, framing, base64 (de)coding — and surface the
 * minimal abstract surface needed by [com.mojentic.realtime.RealtimeVoiceBroker].
 *
 * Per the Gateway Pattern from the monorepo engineering principles, gateways
 * carry **no orchestration**: no event normalisation, no tool dispatch, no
 * audio decoding logic beyond protocol-level framing.
 */
public interface RealtimeGateway {
    /**
     * Open a duplex session for [model] using [config].
     *
     * The returned [RealtimeGatewaySession] is the only stateful surface; the
     * gateway itself is reusable across sessions. [correlationId] threads
     * through any tracer events emitted by the surrounding broker.
     */
    public suspend fun open(
        model: String,
        config: RealtimeVoiceConfig,
        correlationId: String? = null,
    ): RealtimeGatewaySession
}

/**
 * Handle to a single open realtime session.
 *
 * The session is responsible for transport lifecycle and event framing only.
 * Consumers send typed [ClientRealtimeEvent]s and observe a `Flow` of raw
 * provider events; the broker layer normalises raw events into the
 * vendor-neutral [RealtimeEvent] union.
 */
public interface RealtimeGatewaySession {
    /**
     * Cold flow of raw provider events, one element per server message.
     *
     * Each element is the parsed JSON payload from the wire — for OpenAI
     * Realtime, the shape includes a `type` discriminator. The flow completes
     * when the underlying transport closes.
     */
    public val rawEvents: Flow<JsonObject>

    /** Send a typed client event into the session. */
    public suspend fun send(event: ClientRealtimeEvent)

    /** Close the session and tear down the underlying transport. */
    public suspend fun close()
}
