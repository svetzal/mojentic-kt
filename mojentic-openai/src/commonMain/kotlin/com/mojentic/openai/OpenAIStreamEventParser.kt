package com.mojentic.openai

import com.mojentic.llm.CompletionEvidence
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.StreamErrorReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Turns OpenAI chat-completion SSE lines into [CompletionStreamEvent]s for one turn.
 *
 * Success requires a `finish_reason` of `stop` **and** the `data: [DONE]`
 * marker. `[DONE]` after any other finish reason is an incomplete completion.
 * Pure and single-use: feed lines with [accept] until [isTerminal], then call
 * [endOfStream] if the body ended first.
 */
internal class OpenAIStreamEventParser(private val json: Json) {
    private var finishReason: String? = null
    private var usage: JsonObject? = null
    private var providerModel: String? = null

    var isTerminal: Boolean = false
        private set

    fun accept(line: String): List<CompletionStreamEvent> {
        check(!isTerminal) { "stream already reached its terminal event" }
        val payload = ssePayload(line) ?: return emptyList()
        if (payload == DONE) {
            return listOf(
                if (finishReason == STOP) {
                    terminate(CompletionStreamEvent.Completed(evidence()))
                } else {
                    fail(StreamErrorReason.IncompleteCompletion(evidence()))
                },
            )
        }
        val frame = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: return listOf(fail(StreamErrorReason.InvalidStreamEvent(payload)))
        return acceptFrame(frame, payload)
    }

    fun endOfStream(): CompletionStreamEvent {
        val partial = if (finishReason == null && usage == null && providerModel == null) null else evidence()
        return fail(StreamErrorReason.IncompleteStream(partial))
    }

    private fun acceptFrame(frame: JsonObject, payload: String): List<CompletionStreamEvent> {
        frame["error"]?.takeUnless { it is JsonNull }?.let {
            return listOf(fail(StreamErrorReason.ProviderError(detail = it.toString())))
        }
        (frame["model"] as? JsonPrimitive)?.contentOrNull?.let { providerModel = it }
        (frame["usage"] as? JsonObject)?.let { usage = it }
        val choices = frame["choices"] as? JsonArray
            ?: return listOf(fail(StreamErrorReason.InvalidStreamEvent(payload)))
        // A usage-only frame has no choices.
        return choices.firstOrNull()?.let { acceptChoice(it, payload) }.orEmpty()
    }

    private fun acceptChoice(choice: JsonElement, payload: String): List<CompletionStreamEvent> {
        val delta = (choice as? JsonObject)?.get("delta") as? JsonObject
            ?: return listOf(fail(StreamErrorReason.InvalidStreamEvent(payload)))
        (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull?.let { finishReason = it }
        val toolCalls = delta["tool_calls"]
        if (toolCalls is JsonArray && toolCalls.isNotEmpty()) return listOf(fail(StreamErrorReason.UnexpectedToolCalls))
        return acceptContent(delta["content"], payload)
    }

    private fun acceptContent(content: JsonElement?, payload: String): List<CompletionStreamEvent> = when {
        content == null || content is JsonNull -> emptyList()
        content is JsonPrimitive && content.isString -> listOfNotNull(
            content.content.takeIf { it.isNotEmpty() }?.let { CompletionStreamEvent.Content(it) },
        )
        else -> listOf(fail(StreamErrorReason.InvalidStreamEvent(payload)))
    }

    private fun evidence(): CompletionEvidence =
        CompletionEvidence(finishReason = finishReason, usage = usage, providerModel = providerModel)

    private fun fail(reason: StreamErrorReason): CompletionStreamEvent = terminate(CompletionStreamEvent.Error(reason))

    private fun terminate(event: CompletionStreamEvent): CompletionStreamEvent {
        isTerminal = true
        return event
    }

    private fun ssePayload(line: String): String? =
        line.takeIf { it.startsWith(DATA_PREFIX) }?.removePrefix(DATA_PREFIX)?.trim()

    private companion object {
        const val DATA_PREFIX = "data:"
        const val DONE = "[DONE]"
        const val STOP = "stop"
    }
}
