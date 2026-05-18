package com.mojentic.realtime

/**
 * Events the client sends into a realtime session.
 *
 * The gateway is responsible for translating these into the provider's wire
 * format (e.g. OpenAI's `session.update`, `input_audio_buffer.append`, etc.).
 * Consumers never construct OpenAI-shaped JSON directly.
 */
public sealed interface ClientRealtimeEvent {
    /** Set / replace the session config. */
    public data class SessionUpdate(val config: RealtimeVoiceConfig) : ClientRealtimeEvent

    /** Append a PCM frame to the server-side input audio buffer. */
    public data class InputAudioBufferAppend(val frame: AudioFrame) : ClientRealtimeEvent

    /** End the current user turn (only meaningful when [VadConfig.Manual]). */
    public data object InputAudioBufferCommit : ClientRealtimeEvent

    /** Clear any pending audio in the input buffer. */
    public data object InputAudioBufferClear : ClientRealtimeEvent

    /** Insert a text user message into the conversation. */
    public data class UserText(val text: String) : ClientRealtimeEvent

    /** Submit a tool result for a previously-emitted function call. */
    public data class FunctionCallOutput(val callId: String, val output: String) : ClientRealtimeEvent

    /** Ask the model to produce the next response. */
    public data object ResponseCreate : ClientRealtimeEvent

    /** Cancel an in-flight response (used for interruption / barge-in). */
    public data object ResponseCancel : ClientRealtimeEvent
}
