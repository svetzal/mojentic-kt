package com.mojentic.realtime.internal

/**
 * String constants for the OpenAI Realtime wire `type` discriminator.
 *
 * Lives in `mojentic-core` because the broker layer normalises raw gateway
 * events into the vendor-neutral [com.mojentic.realtime.RealtimeEvent]
 * union; that translation needs to look at the discriminator. The gateway
 * itself only sees raw JSON.
 *
 * If we later add a second provider (e.g. Anthropic realtime), this file
 * splits per-provider and the normaliser fans out behind an interface.
 */
internal object OpenAiEventNames {
    const val SESSION_CREATED = "session.created"
    const val SESSION_UPDATED = "session.updated"

    const val SPEECH_STARTED = "input_audio_buffer.speech_started"
    const val SPEECH_STOPPED = "input_audio_buffer.speech_stopped"
    const val USER_TRANSCRIPT_COMPLETED = "conversation.item.input_audio_transcription.completed"
    const val USER_TRANSCRIPT_DELTA = "conversation.item.input_audio_transcription.delta"

    const val RESPONSE_CREATED = "response.created"
    const val RESPONSE_TEXT_DELTA = "response.text.delta"
    const val RESPONSE_TEXT_DONE = "response.text.done"
    const val RESPONSE_AUDIO_DELTA = "response.audio.delta"
    const val RESPONSE_AUDIO_TRANSCRIPT_DELTA = "response.audio_transcript.delta"
    const val RESPONSE_AUDIO_TRANSCRIPT_DONE = "response.audio_transcript.done"
    const val RESPONSE_OUTPUT_ITEM_ADDED = "response.output_item.added"
    const val RESPONSE_OUTPUT_ITEM_DONE = "response.output_item.done"
    const val RESPONSE_FUNCTION_ARGS_DELTA = "response.function_call_arguments.delta"
    const val RESPONSE_FUNCTION_ARGS_DONE = "response.function_call_arguments.done"
    const val RESPONSE_DONE = "response.done"

    const val RATE_LIMITS_UPDATED = "rate_limits.updated"
    const val ERROR = "error"
}
