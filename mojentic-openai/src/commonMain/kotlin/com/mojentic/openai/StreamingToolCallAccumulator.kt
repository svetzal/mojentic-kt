package com.mojentic.openai

import com.mojentic.llm.LlmToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Accumulates partial OpenAI tool-call deltas from a streaming response.
 *
 * The OpenAI SSE stream splits a single tool call across several chunks,
 * keyed by `index`. Each chunk contributes a prefix of the `arguments`
 * string, and the `id`/`name` typically arrive on the first chunk for that
 * index. This accumulator stitches them back together before emitting a
 * neutral [LlmToolCall] to the broker.
 */
internal class StreamingToolCallAccumulator(private val json: Json) {
    private data class Pending(
        var id: String? = null,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder(),
    )

    private val pending = linkedMapOf<Int, Pending>()
    private var fallbackIndex = 0

    fun append(delta: OpenAIToolCall) {
        val index = delta.index ?: fallbackIndex++
        val slot = pending.getOrPut(index) { Pending() }
        if (delta.id != null) slot.id = delta.id
        if (delta.function.name.isNotEmpty()) slot.name = delta.function.name
        slot.arguments.append(delta.function.arguments)
    }

    fun toLlmToolCalls(): List<LlmToolCall> = pending.entries
        .sortedBy { it.key }
        .map { it.value }
        .filter { it.name != null }
        .map { slot ->
            val args = runCatching { json.parseToJsonElement(slot.arguments.toString()) as? JsonObject }
                .getOrNull() ?: buildJsonObject { put("_raw", JsonPrimitive(slot.arguments.toString())) }
            LlmToolCall(id = slot.id, name = slot.name ?: "", arguments = args)
        }
}
