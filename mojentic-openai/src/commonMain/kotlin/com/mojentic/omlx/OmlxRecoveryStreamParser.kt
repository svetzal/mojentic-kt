package com.mojentic.omlx

import com.mojentic.llm.CompletionEvidence
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmToolCall
import com.mojentic.llm.recovery.RecoveryStream
import com.mojentic.llm.recovery.RecoveryStreamClosedException
import com.mojentic.llm.recovery.RecoveryStreamException
import com.mojentic.openai.OpenAIChatResponse
import com.mojentic.openai.OpenAIResponseMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Strict single-attempt parser reserved for recovery; legacy parsing remains unchanged. */
internal class OmlxRecoveryStreamParser(private val json: Json) {
    private data class Tool(
        val id: StringBuilder = StringBuilder(),
        val name: StringBuilder = StringBuilder(),
        val arguments: StringBuilder = StringBuilder(),
    )

    private val tools = mutableMapOf<Int, Tool>()
    private var evidence = CompletionEvidence()

    suspend fun consume(
        stream: RecoveryStream,
        allowTools: Boolean,
        emit: suspend (GatewayStreamEvent) -> Unit,
    ): CompletionEvidence {
        while (true) {
            val payload = readPayload(stream)
            if (payload == "[DONE]") {
                finish(stream, allowTools, emit)
                return evidence
            }
            val chunk = decode(payload, stream)
            val choice = chunk.choices.firstOrNull()
            appendTools(choice?.delta, allowTools)
            evidence = evidence.copy(
                finishReason = choice?.finishReason ?: evidence.finishReason,
                usage = chunk.usage ?: evidence.usage,
                providerModel = chunk.model ?: evidence.providerModel,
            )
            stream.progress()
            chunk.usage?.let { stream.metrics(it, null) }
            deliver(choice?.delta, stream, allowTools, emit)
        }
    }

    private suspend fun readPayload(stream: RecoveryStream): String {
        while (true) {
            val line = stream.readLine() ?: throw RecoveryStreamClosedException()
            if (ignoredField(line) || isKeepAliveFrame(line, json)) continue
            if (!line.startsWith("data:")) throw RecoveryStreamException()
            return line.removePrefix("data:").trim()
        }
    }

    private fun ignoredField(line: String): Boolean =
        line.isBlank() || line.startsWith(":") || line.startsWith("event:") || line.startsWith("id:")

    private fun decode(payload: String, stream: RecoveryStream): OpenAIChatResponse = try {
        val root = json.parseToJsonElement(payload) as JsonObject
        require(root["error"] == null || root["error"] is JsonNull)
        require(optionalText(root["model"]))
        (root["choices"] as JsonArray).forEach { validateChoice(it as JsonObject) }
        validateUsage(root["usage"])
        json.decodeFromJsonElement(OpenAIChatResponse.serializer(), root).also {
            require(it.choices.size == 1 || (it.choices.isEmpty() && it.usage != null))
        }
    } catch (cause: Exception) {
        stream.invalid(cause)
    }

    private fun validateChoice(choice: JsonObject) {
        require(optionalText(choice["finish_reason"]))
        val delta = choice["delta"] as JsonObject
        require(optionalText(delta["content"]) && optionalText(delta["reasoning_content"]))
        val calls = delta["tool_calls"]
        require(calls == null || calls is JsonNull || calls is JsonArray)
        (calls as? JsonArray)?.forEach { validateTool(it as JsonObject) }
    }

    private fun validateTool(call: JsonObject) {
        val index = call["index"] as? JsonPrimitive
        require(index != null && !index.isString && index.longOrNull?.let { it >= 0 } == true)
        require(optionalText(call["id"]))
        val function = call["function"] as JsonObject
        require(optionalText(function["name"]) && optionalText(function["arguments"]))
    }

    private fun validateUsage(usage: JsonElement?) {
        require(usage == null || usage is JsonNull || usage is JsonObject)
        (usage as? JsonObject)?.let {
            for (key in USAGE_KEYS) require(optionalCount(it[key]))
        }
    }

    private fun optionalCount(value: JsonElement?): Boolean = value == null || value is JsonNull ||
        (value is JsonPrimitive && !value.isString && value.longOrNull?.let { it >= 0 } == true)

    private fun optionalText(value: JsonElement?): Boolean =
        value == null || value is JsonNull || (value is JsonPrimitive && value.isString)

    private fun appendTools(delta: OpenAIResponseMessage?, allowTools: Boolean) {
        delta?.toolCalls.orEmpty().forEach { call ->
            if (!allowTools) throw RecoveryStreamException()
            val index = call.index ?: throw RecoveryStreamException()
            val tool = tools.getOrPut(index) { Tool() }
            call.id?.let { tool.id.append(it) }
            tool.name.append(call.function.name)
            tool.arguments.append(call.function.arguments)
        }
    }

    private suspend fun deliver(
        delta: OpenAIResponseMessage?,
        stream: RecoveryStream,
        allowTools: Boolean,
        emit: suspend (GatewayStreamEvent) -> Unit,
    ) {
        delta?.content?.takeIf { it.isNotEmpty() }?.let {
            stream.delivered(content = it)
            emit(GatewayStreamEvent.Content(it))
        }
        delta?.reasoningContent?.takeIf { it.isNotEmpty() && allowTools }?.let {
            stream.delivered(reasoning = it)
            emit(GatewayStreamEvent.Thinking(it))
        }
    }

    private suspend fun finish(stream: RecoveryStream, allowTools: Boolean, emit: suspend (GatewayStreamEvent) -> Unit) {
        val validFinish = evidence.finishReason == "stop" || (allowTools && evidence.finishReason == "tool_calls")
        if (!validFinish) throw RecoveryStreamException(evidence)
        val calls = buildTools(stream)
        stream.completedTools(calls.size)
        stream.finish()
        if (calls.isNotEmpty()) {
            stream.delivered(toolCalls = calls.size)
            emit(GatewayStreamEvent.ToolCalls(calls))
        }
    }

    private fun buildTools(stream: RecoveryStream): List<LlmToolCall> = try {
        tools.entries.sortedBy { it.key }.map { (_, tool) ->
            require(tool.name.isNotEmpty())
            val arguments = json.parseToJsonElement(tool.arguments.toString()) as JsonObject
            LlmToolCall(tool.id.toString().ifEmpty { null }, tool.name.toString(), arguments)
        }
    } catch (cause: Exception) {
        stream.invalid(cause)
    }

    private companion object {
        val USAGE_KEYS = setOf("prompt_tokens", "completion_tokens", "total_tokens")
    }
}
