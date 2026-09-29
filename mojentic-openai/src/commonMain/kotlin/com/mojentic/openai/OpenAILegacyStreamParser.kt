package com.mojentic.openai

import com.mojentic.llm.GatewayStreamEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * Turns OpenAI chat-completion SSE lines into legacy [GatewayStreamEvent]s.
 *
 * Content and reasoning deltas become events as they arrive. Tool-call deltas
 * are stitched together and come out once, from [finish]. Malformed chunks are
 * logged and skipped. Feed lines with [accept] until [isDone] or the body
 * ends, then call [finish]. Single-use.
 */
internal class OpenAILegacyStreamParser(private val json: Json) {
    private val accumulator = StreamingToolCallAccumulator(json)

    /** True once the `data: [DONE]` marker has arrived. */
    var isDone: Boolean = false
        private set

    fun accept(line: String): List<GatewayStreamEvent> {
        val payload = ssePayload(line) ?: return emptyList()
        if (payload == DONE) {
            isDone = true
            return emptyList()
        }
        val delta = parseDelta(payload) ?: return emptyList()
        delta.toolCalls?.forEach { accumulator.append(it) }
        return listOfNotNull(
            delta.content?.takeIf { it.isNotEmpty() }?.let { GatewayStreamEvent.Content(it) },
            delta.reasoningContent?.takeIf { it.isNotEmpty() }?.let { GatewayStreamEvent.Thinking(it) },
        )
    }

    /** The accumulated tool calls as one [GatewayStreamEvent.ToolCalls], or nothing when there were none. */
    fun finish(): List<GatewayStreamEvent> =
        listOfNotNull(accumulator.toLlmToolCalls().takeIf { it.isNotEmpty() }?.let { GatewayStreamEvent.ToolCalls(it) })

    private fun parseDelta(payload: String): OpenAIResponseMessage? {
        val chunk = runCatching { json.decodeFromString(OpenAIChatResponse.serializer(), payload) }
            .getOrElse {
                logger.warn { "Skipping malformed OpenAI SSE chunk: $payload" }
                null
            }
        return chunk?.choices?.firstOrNull()?.delta
    }

    private fun ssePayload(line: String): String? =
        line.takeIf { it.startsWith(DATA_PREFIX) }?.removePrefix(DATA_PREFIX)?.trim()

    private companion object {
        const val DATA_PREFIX = "data:"
        const val DONE = "[DONE]"
    }
}
