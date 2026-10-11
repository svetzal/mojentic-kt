package com.mojentic.anthropic

import com.mojentic.anthropic.internal.AnthropicDelta
import com.mojentic.anthropic.internal.AnthropicResponseContent
import com.mojentic.anthropic.internal.AnthropicStreamEvent
import com.mojentic.llm.CompletionEvidence
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmToolCall
import com.mojentic.llm.recovery.RecoveryProviderResponseException
import com.mojentic.llm.recovery.RecoveryStream
import com.mojentic.llm.recovery.RecoveryStreamClosedException
import com.mojentic.llm.recovery.RecoveryStreamException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Strict opt-in decoder; the legacy accumulator remains unchanged. */
internal class AnthropicRecoveryStreamParser(private val json: Json) {
    private val blocks = mutableMapOf<Int, AnthropicResponseContent>()
    private val arguments = mutableMapOf<Int, StringBuilder>()
    private val tools = mutableListOf<LlmToolCall>()
    private var started = false
    private var terminalDelta = false
    private val closedBlocks = mutableSetOf<Int>()
    private var completion = CompletionEvidence()

    suspend fun consume(stream: RecoveryStream, emit: suspend (GatewayStreamEvent) -> Unit) {
        while (true) {
            val line = stream.readLine() ?: throw RecoveryStreamClosedException()
            if (ignoredField(line)) continue
            if (dispatch(decode(line, stream), stream, emit)) return
        }
    }

    private fun ignoredField(line: String): Boolean =
        line.isBlank() || listOf(":", "event:", "id:").any { line.startsWith(it) }

    private fun decode(line: String, stream: RecoveryStream): AnthropicStreamEvent {
        if (!line.startsWith("data:")) invalid()
        return try {
            json.decodeFromString(AnthropicStreamEvent.serializer(), line.removePrefix("data:").trim())
        } catch (cause: Exception) {
            stream.invalid(cause)
        }
    }

    private suspend fun dispatch(
        event: AnthropicStreamEvent,
        stream: RecoveryStream,
        emit: suspend (GatewayStreamEvent) -> Unit,
    ): Boolean {
        when (event) {
            is AnthropicStreamEvent.MessageStart -> startMessage(event, stream)

            is AnthropicStreamEvent.ContentBlockStart -> startBlock(event)

            is AnthropicStreamEvent.ContentBlockDelta -> delta(event, stream, emit)

            is AnthropicStreamEvent.ContentBlockStop -> stopBlock(event, stream)

            is AnthropicStreamEvent.MessageDelta -> messageDelta(event, stream)

            is AnthropicStreamEvent.MessageStop -> {
                finish(stream, emit)
                return true
            }

            is AnthropicStreamEvent.Error -> throw RecoveryProviderResponseException(event)

            is AnthropicStreamEvent.Ping -> Unit
        }
        return false
    }

    private fun invalid(): Nothing = throw RecoveryStreamException()

    private fun startMessage(event: AnthropicStreamEvent.MessageStart, stream: RecoveryStream) {
        if (started) invalid()
        started = true
        completion = completion.copy(providerModel = event.message.model, usage = event.message.usage)
        stream.completionEvidence(completion)
    }

    private fun startBlock(event: AnthropicStreamEvent.ContentBlockStart) {
        if (!started || terminalDelta) invalid()
        if (event.index < 0 || event.index in closedBlocks) invalid()
        if (blocks.put(event.index, event.contentBlock) != null) invalid()
        when (val block = event.contentBlock) {
            is AnthropicResponseContent.ToolUse -> arguments[event.index] = StringBuilder()
            is AnthropicResponseContent.Text -> Unit
            is AnthropicResponseContent.Thinking -> Unit
        }
    }

    private suspend fun delta(
        event: AnthropicStreamEvent.ContentBlockDelta,
        stream: RecoveryStream,
        emit: suspend (GatewayStreamEvent) -> Unit,
    ) {
        val block = blocks[event.index] ?: invalid()
        when (val delta = event.delta) {
            is AnthropicDelta.TextDelta -> {
                if (block !is AnthropicResponseContent.Text) invalid()
                deliver(stream, GatewayStreamEvent.Content(delta.text), emit)
            }

            is AnthropicDelta.ThinkingDelta -> {
                if (block !is AnthropicResponseContent.Thinking) invalid()
                deliver(stream, GatewayStreamEvent.Thinking(delta.thinking), emit)
            }

            is AnthropicDelta.InputJsonDelta -> {
                val buffer = arguments[event.index] ?: invalid()
                buffer.append(delta.partialJson)
            }

            is AnthropicDelta.SignatureDelta -> {
                if (block !is AnthropicResponseContent.Thinking) invalid()
            }
        }
    }

    private fun stopBlock(event: AnthropicStreamEvent.ContentBlockStop, stream: RecoveryStream) {
        val block = blocks.remove(event.index) ?: invalid()
        closedBlocks += event.index
        if (block is AnthropicResponseContent.ToolUse) {
            val raw = arguments.remove(event.index).toString()
            val input = try {
                if (raw.isEmpty()) block.input else json.parseToJsonElement(raw) as JsonObject
            } catch (cause: Exception) {
                stream.invalid(cause)
            }
            tools += LlmToolCall(block.id, block.name, input)
            stream.completedTools(tools.size)
        }
    }

    private fun messageDelta(event: AnthropicStreamEvent.MessageDelta, stream: RecoveryStream) {
        if (!started || terminalDelta || blocks.isNotEmpty()) invalid()
        terminalDelta = true
        val usage = completion.usage.orEmpty().toMutableMap()
        event.usage?.inputTokens?.let { usage["input_tokens"] = JsonPrimitive(it) }
        event.usage?.outputTokens?.let { usage["output_tokens"] = JsonPrimitive(it) }
        completion = completion.copy(
            finishReason = event.delta.stopReason,
            usage = usage.takeIf { it.isNotEmpty() }?.let { JsonObject(it) },
        )
        stream.completionEvidence(completion)
    }

    private suspend fun finish(stream: RecoveryStream, emit: suspend (GatewayStreamEvent) -> Unit) {
        if (!started || !terminalDelta) invalid()
        if (blocks.isNotEmpty() || completion.finishReason !in FINISH_REASONS) {
            throw RecoveryStreamException(completion)
        }
        stream.finish()
        if (tools.isNotEmpty()) {
            stream.delivered(toolCalls = tools.size)
            emit(GatewayStreamEvent.ToolCalls(tools.toList()))
        }
    }

    private suspend fun deliver(stream: RecoveryStream, event: GatewayStreamEvent, emit: suspend (GatewayStreamEvent) -> Unit) {
        when (event) {
            is GatewayStreamEvent.Content -> if (event.text.isNotEmpty()) {
                stream.delivered(content = event.text)
                emit(event)
            }

            is GatewayStreamEvent.Thinking -> if (event.text.isNotEmpty()) {
                stream.delivered(reasoning = event.text)
                emit(event)
            }

            else -> invalid()
        }
    }

    private companion object {
        val FINISH_REASONS = setOf(
            "end_turn",
            "stop_sequence",
            "tool_use",
            "max_tokens",
            "pause_turn",
            "refusal",
            "model_context_window_exceeded",
        )
    }
}
