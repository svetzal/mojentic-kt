package com.mojentic.realtime

import com.mojentic.llm.LlmToolCall
import kotlinx.serialization.json.JsonObject

/**
 * Vendor-neutral event observed during a realtime session.
 *
 * Mirrors the design in `REALTIME.md` §9b — names are framed in terms of the
 * conversation a developer is observing, not OpenAI's wire-level event
 * vocabulary. The raw provider events remain available via
 * [com.mojentic.realtime.RealtimeGatewaySession.rawEvents] for power users.
 *
 * The union is intentionally large rather than collapsed-and-then-recovered:
 * UI consumers subscribe to `*Delta` events; loggers / tests subscribe to the
 * non-delta variants. Both fire — consumers don't reassemble.
 */
public sealed interface RealtimeEvent {

    // -- Session lifecycle --

    public data class SessionOpened(val sessionId: String) : RealtimeEvent
    public data class SessionUpdated(val instructions: String?) : RealtimeEvent
    public data class SessionClosed(val reason: CloseReason) : RealtimeEvent

    public enum class CloseReason { Client, Server, Error }

    // -- User turn --

    public data class UserSpeechStarted(val itemId: String?) : RealtimeEvent
    public data class UserSpeechStopped(val itemId: String?) : RealtimeEvent
    public data class UserTranscriptDelta(val itemId: String, val delta: String) : RealtimeEvent
    public data class UserTranscript(val itemId: String, val text: String) : RealtimeEvent

    // -- Assistant turn --

    public data class AssistantTurnStarted(val turnId: String) : RealtimeEvent
    public data class AssistantTextDelta(val turnId: String, val delta: String) : RealtimeEvent
    public data class AssistantText(val turnId: String, val text: String) : RealtimeEvent
    public data class AssistantTranscriptDelta(val turnId: String, val delta: String) : RealtimeEvent
    public data class AssistantTranscript(val turnId: String, val text: String) : RealtimeEvent
    public data class AssistantAudioDelta(val turnId: String, val frame: AudioFrame) : RealtimeEvent
    public data class AssistantTurnCompleted(
        val turnId: String,
        val inputTokens: Int? = null,
        val outputTokens: Int? = null,
        val totalTokens: Int? = null,
    ) : RealtimeEvent

    // -- Tool calls (parallel-aware) --

    public data class ToolCallStarted(val turnId: String, val callId: String, val name: String) : RealtimeEvent
    public data class ToolCallArgsDelta(val callId: String, val delta: String) : RealtimeEvent
    public data class ToolCallDispatched(val callId: String, val call: LlmToolCall) : RealtimeEvent
    public data class ToolCallCompleted(val callId: String, val name: String, val result: String) : RealtimeEvent
    public data class ToolCallFailed(val callId: String, val name: String, val error: Throwable) : RealtimeEvent
    public data class ToolBatchSubmitted(val turnId: String, val callIds: List<String>) : RealtimeEvent

    // -- Control --

    public data class Interrupted(val turnId: String?, val reason: InterruptionReason) : RealtimeEvent
    public data class RateLimited(val resetMs: Long?, val details: JsonObject) : RealtimeEvent
    public data class GatewayError(val message: String, val recoverable: Boolean) : RealtimeEvent

    public enum class InterruptionReason { BargeIn, Manual, Error }
}
