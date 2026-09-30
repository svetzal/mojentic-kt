package com.mojentic.ollama

import com.mojentic.llm.CompletionEvidence
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.StreamErrorReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Turns Ollama `/api/chat` NDJSON lines into [CompletionStreamEvent]s for one turn.
 *
 * Success requires a final frame with `done: true` and `done_reason: "stop"`.
 * Any other `done_reason`, including a missing one, is an incomplete
 * completion. Pure and single-use: feed lines with [accept] until
 * [isTerminal], then call [endOfStream] if the body ended first.
 */
internal class OllamaStreamEventParser(private val json: Json) {
    private var providerModel: String? = null
    private var usage: JsonObject? = null
    private var metadata: JsonObject? = null
    private var finishReason: String? = null

    var isTerminal: Boolean = false
        private set

    fun accept(line: String): List<CompletionStreamEvent> {
        check(!isTerminal) { "stream already reached its terminal event" }
        if (line.isBlank()) return emptyList()
        val frame = runCatching { json.parseToJsonElement(line) as? JsonObject }.getOrNull()
            ?: return listOf(fail(StreamErrorReason.InvalidStreamEvent(line)))
        return acceptFrame(frame, line)
    }

    private fun acceptFrame(frame: JsonObject, line: String): List<CompletionStreamEvent> {
        frame["error"]?.takeUnless { it is JsonNull }?.let {
            return listOf(fail(StreamErrorReason.ProviderError(detail = it.toString())))
        }
        val validDone = (frame["done"] as? JsonPrimitive)?.let { !it.isString && it.booleanOrNull != null } == true
        if (!validDone || !optionalString(frame["model"]) || !optionalString(frame["done_reason"])) {
            return listOf(fail(StreamErrorReason.InvalidStreamEvent(line)))
        }
        val chunk = runCatching { json.decodeFromJsonElement(OllamaChatResponse.serializer(), frame) }.getOrNull()
            ?: return listOf(fail(StreamErrorReason.InvalidStreamEvent(line)))
        return acceptChunk(chunk)
    }

    fun endOfStream(): CompletionStreamEvent =
        fail(StreamErrorReason.IncompleteStream(evidence().takeUnless { it == CompletionEvidence() }))

    private fun acceptChunk(chunk: OllamaChatResponse): List<CompletionStreamEvent> {
        chunk.model?.let { providerModel = it }
        chunk.doneReason?.let { finishReason = it }
        chunk.reportedUsage?.let { usage = JsonObject(usage.orEmpty() + it) }
        chunk.reportedMetadata?.let { metadata = JsonObject(metadata.orEmpty() + it) }
        if (!chunk.message.toolCalls.isNullOrEmpty()) return listOf(fail(StreamErrorReason.UnexpectedToolCalls))
        val content = chunk.message.content?.takeIf { it.isNotEmpty() }?.let { CompletionStreamEvent.Content(it) }
        if (!chunk.done) return listOfNotNull(content)
        val evidence = evidence().copy(finishReason = chunk.doneReason)
        val terminal = if (chunk.doneReason == STOP) {
            terminate(CompletionStreamEvent.Completed(evidence))
        } else {
            fail(StreamErrorReason.IncompleteCompletion(evidence))
        }
        return listOfNotNull(content, terminal)
    }

    private fun optionalString(value: JsonElement?): Boolean =
        value == null || value is JsonNull || (value is JsonPrimitive && value.isString)

    private fun evidence(): CompletionEvidence = CompletionEvidence(
        finishReason = finishReason,
        usage = usage,
        providerModel = providerModel,
        metadata = metadata,
    )

    private fun fail(reason: StreamErrorReason): CompletionStreamEvent = terminate(CompletionStreamEvent.Error(reason))

    private fun terminate(event: CompletionStreamEvent): CompletionStreamEvent {
        isTerminal = true
        return event
    }

    private companion object {
        const val STOP = "stop"
    }
}
