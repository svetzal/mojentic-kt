package com.mojentic.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * What the provider reported about how a streamed turn ended.
 *
 * Every field is null when the provider did not report it. Usage is never
 * estimated.
 *
 * @property finishReason Provider finish reason, for example `stop` or `length`.
 * @property usage Token usage as the provider reported it.
 * @property providerModel Model name the provider reported.
 * @property metadata Other provider-reported metadata, for example Ollama durations.
 */
public data class CompletionEvidence(
    val finishReason: String? = null,
    val usage: JsonObject? = null,
    val providerModel: String? = null,
    val metadata: JsonObject? = null,
)

/**
 * Event emitted by [LlmBroker.generateStreamEvents] for a single turn.
 *
 * Zero or more [Content] events arrive in order, then exactly one terminal
 * event: [Completed] or [Error]. Nothing follows the terminal event.
 *
 * Content that arrived before an [Error] is evidence of what the provider
 * sent, not a result. Treat the turn as failed.
 */
public sealed interface CompletionStreamEvent {
    /** Visible assistant content, in arrival order. */
    public data class Content(val text: String) : CompletionStreamEvent

    /** Terminal success: the provider reported a normal stop and ended the stream. */
    public data class Completed(val evidence: CompletionEvidence) : CompletionStreamEvent

    /** Terminal failure. See [StreamErrorReason] for the causes. */
    public data class Error(val reason: StreamErrorReason) : CompletionStreamEvent
}

/**
 * Why a [LlmBroker.generateStreamEvents] turn failed.
 */
public sealed interface StreamErrorReason {
    /**
     * The provider ended the turn with a finish reason other than `stop`
     * (for example `length`), or without one.
     */
    public data class IncompleteCompletion(val evidence: CompletionEvidence) : StreamErrorReason

    /**
     * The stream ended without a terminal marker. [evidence] holds whatever
     * arrived before the end, or is null when nothing did.
     */
    public data class IncompleteStream(val evidence: CompletionEvidence?) : StreamErrorReason

    /**
     * The provider sent an error frame, or answered with a non-2xx HTTP
     * [status]. [detail] is the provider's error payload or response body.
     */
    public data class ProviderError(val detail: String, val status: Int? = null) : StreamErrorReason

    /** The provider streamed a native tool call. This API supplies no tools and executes none. */
    public data object UnexpectedToolCalls : StreamErrorReason

    /** A stream frame could not be parsed or had an unexpected shape. */
    public data class InvalidStreamEvent(val payload: String) : StreamErrorReason

    /** The gateway does not implement [StreamEventsGateway]. No request was sent. */
    public data object StreamEventsUnsupported : StreamErrorReason

    /** The connection or the body read failed. */
    public data class RequestFailed(val cause: Throwable) : StreamErrorReason
}

/**
 * Optional gateway capability behind [LlmBroker.generateStreamEvents].
 *
 * An implementation sends exactly one streaming request per collection,
 * supplies no tools, and emits [CompletionStreamEvent.Content] events followed
 * by exactly one terminal event. It reports failures as
 * [CompletionStreamEvent.Error] rather than by throwing. Cancelling the
 * collector cancels the request.
 */
public interface StreamEventsGateway {
    /**
     * Stream one turn as [CompletionStreamEvent]s.
     *
     * @param model Provider-side model identifier.
     * @param messages Conversation history.
     * @param config Completion knobs, including [CompletionConfig.responseFormat].
     */
    public fun streamEvents(
        model: String,
        messages: List<LlmMessage>,
        config: CompletionConfig = CompletionConfig(),
    ): Flow<CompletionStreamEvent>
}
