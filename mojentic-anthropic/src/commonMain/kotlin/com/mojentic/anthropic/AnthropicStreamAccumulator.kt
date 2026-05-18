package com.mojentic.anthropic

import com.mojentic.anthropic.internal.AnthropicDelta
import com.mojentic.anthropic.internal.AnthropicResponseContent
import com.mojentic.anthropic.internal.AnthropicStreamEvent
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Accumulates Anthropic SSE events and emits Mojentic gateway stream events.
 *
 * Text deltas surface immediately as [GatewayStreamEvent.Content].
 * Thinking deltas surface immediately as [GatewayStreamEvent.Thinking].
 * Tool-use content blocks accumulate `partial_json` chunks until the matching
 * `content_block_stop` arrives, then are finalised in [toolCalls] (drained by
 * the gateway when the stream completes).
 */
internal class AnthropicStreamAccumulator(private val json: Json) {
    private val pendingTools: MutableMap<Int, PendingTool> = mutableMapOf()
    private val completedTools: MutableList<LlmToolCall> = mutableListOf()

    /**
     * Parse one SSE `data:` payload and emit any user-visible events to [emit].
     *
     * @return `true` if the stream terminates with this event (`message_stop`).
     */
    suspend fun feed(payload: String, emit: suspend (GatewayStreamEvent) -> Unit): Boolean {
        val event = json.decodeFromString(AnthropicStreamEvent.serializer(), payload)
        return dispatch(event, emit)
    }

    /**
     * Tool calls finalised across the stream. Called by the gateway after the
     * channel closes.
     */
    fun toolCalls(): List<LlmToolCall> = completedTools.toList()

    private suspend fun dispatch(
        event: AnthropicStreamEvent,
        emit: suspend (GatewayStreamEvent) -> Unit,
    ): Boolean = when (event) {
        is AnthropicStreamEvent.ContentBlockStart -> {
            handleBlockStart(event)
            false
        }
        is AnthropicStreamEvent.ContentBlockDelta -> {
            handleDelta(event, emit)
            false
        }
        is AnthropicStreamEvent.ContentBlockStop -> {
            finaliseBlock(event.index)
            false
        }
        is AnthropicStreamEvent.MessageStop -> true
        else -> false
    }

    private fun handleBlockStart(event: AnthropicStreamEvent.ContentBlockStart) {
        when (val block = event.contentBlock) {
            is AnthropicResponseContent.ToolUse -> {
                pendingTools[event.index] = PendingTool(
                    id = block.id,
                    name = block.name,
                    argsBuffer = StringBuilder(),
                )
            }
            else -> Unit
        }
    }

    private suspend fun handleDelta(
        event: AnthropicStreamEvent.ContentBlockDelta,
        emit: suspend (GatewayStreamEvent) -> Unit,
    ) {
        when (val delta = event.delta) {
            is AnthropicDelta.TextDelta ->
                if (delta.text.isNotEmpty()) emit(GatewayStreamEvent.Content(delta.text))
            is AnthropicDelta.ThinkingDelta ->
                if (delta.thinking.isNotEmpty()) emit(GatewayStreamEvent.Thinking(delta.thinking))
            is AnthropicDelta.InputJsonDelta -> pendingTools[event.index]?.argsBuffer?.append(delta.partialJson)
            is AnthropicDelta.SignatureDelta -> Unit
        }
    }

    private fun finaliseBlock(index: Int) {
        val pending = pendingTools.remove(index) ?: return
        val args = parseArgs(pending.argsBuffer.toString())
        completedTools.add(LlmToolCall(id = pending.id, name = pending.name, arguments = args))
    }

    private fun parseArgs(raw: String): JsonObject {
        if (raw.isEmpty()) return buildJsonObject {}
        return runCatching { json.parseToJsonElement(raw) as? JsonObject }
            .getOrNull()
            ?: buildJsonObject {}
    }

    private data class PendingTool(
        val id: String,
        val name: String,
        val argsBuffer: StringBuilder,
    )
}
