package com.mojentic.ollama

import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.recovery.RecoveryStream
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/** Validate wire types before decoding, so malformed frames never manufacture telemetry. */
internal fun Json.decodeRecoveryFrame(line: String): OllamaChatResponse {
    val frame = parseToJsonElement(line) as JsonObject
    require(frame["error"] == null || frame["error"] is JsonNull)
    val done = frame["done"] as? JsonPrimitive
    require(done != null && !done.isString && done.booleanOrNull != null)
    require(optionalText(frame["model"]) && optionalText(frame["done_reason"]))
    for (key in COUNT_KEYS) require(optionalCount(frame[key]))
    val message = frame["message"] as JsonObject
    require(optionalText(message["content"]) && optionalText(message["thinking"]))
    val tools = message["tool_calls"]
    require(tools == null || tools is JsonNull || tools is JsonArray)
    (tools as? JsonArray)?.forEach { call ->
        val function = (call as JsonObject)["function"] as JsonObject
        val name = function["name"] as? JsonPrimitive
        require(name?.isString == true && name.content.isNotEmpty())
        require(function["arguments"] is JsonObject)
    }
    return decodeFromJsonElement(OllamaChatResponse.serializer(), frame)
}

private fun optionalText(value: JsonElement?): Boolean = value == null || value is JsonNull || (value is JsonPrimitive && value.isString)

private val COUNT_KEYS =
    setOf("prompt_eval_count", "eval_count", "total_duration", "load_duration", "prompt_eval_duration", "eval_duration")

private fun optionalCount(value: JsonElement?): Boolean = value == null || value is JsonNull ||
    (value is JsonPrimitive && !value.isString && value.longOrNull?.let { it >= 0 } == true)

/** Direct delivery keeps cancellation and consumer suspension inside the actual attempt. */
internal suspend fun FlowCollector<GatewayStreamEvent>.emitRecoveryChunk(
    chunk: OllamaChatResponse,
    stream: RecoveryStream,
) {
    chunk.message.content?.takeIf { it.isNotEmpty() }?.let {
        stream.delivered(content = it)
        emit(GatewayStreamEvent.Content(it))
    }
    chunk.message.thinking?.takeIf { it.isNotEmpty() }?.let {
        stream.delivered(reasoning = it)
        emit(GatewayStreamEvent.Thinking(it))
    }
}
