package com.mojentic.ollama

import com.mojentic.llm.ResponseFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
internal data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions? = null,
    val tools: List<OllamaToolWire>? = null,
    val format: JsonElement? = null,
    val think: Boolean? = null,
)

/** Ollama's `format` field: omitted for text, `"json"` for JSON mode, or the schema itself. */
internal fun ResponseFormat.toOllamaFormat(): JsonElement? = when (this) {
    ResponseFormat.Text -> null
    is ResponseFormat.Json -> schema ?: JsonPrimitive("json")
}

@Serializable
internal data class OllamaOptions(
    val temperature: Double? = null,
    @SerialName("num_ctx") val numCtx: Int? = null,
    @SerialName("num_predict") val numPredict: Int? = null,
)

@Serializable
internal data class OllamaMessage(
    val role: String,
    val content: String? = null,
    val images: List<String>? = null,
    @SerialName("tool_calls") val toolCalls: List<OllamaToolCall>? = null,
    val thinking: String? = null,
)

@Serializable
internal data class OllamaToolCall(val function: OllamaToolFunction)

@Serializable
internal data class OllamaToolFunction(val name: String, val arguments: JsonObject)

@Serializable
internal data class OllamaToolWire(val type: String = "function", val function: OllamaToolWireFunction)

@Serializable
internal data class OllamaToolWireFunction(val name: String, val description: String, val parameters: JsonObject)

@Serializable
internal data class OllamaChatResponse(
    val model: String? = null,
    val message: OllamaMessage,
    val done: Boolean = false,
    @SerialName("done_reason") val doneReason: String? = null,
    @SerialName("prompt_eval_count") val promptEvalCount: Long? = null,
    @SerialName("eval_count") val evalCount: Long? = null,
    @SerialName("total_duration") val totalDuration: Long? = null,
    @SerialName("load_duration") val loadDuration: Long? = null,
    @SerialName("prompt_eval_duration") val promptEvalDuration: Long? = null,
    @SerialName("eval_duration") val evalDuration: Long? = null,
) {
    /** Token counts Ollama reported, under Ollama's own keys; null when it reported none. */
    val reportedUsage: JsonObject?
        get() = reportedFields("prompt_eval_count" to promptEvalCount, "eval_count" to evalCount)

    /** Timings Ollama reported, in nanoseconds under Ollama's own keys; null when it reported none. */
    val reportedMetadata: JsonObject?
        get() = reportedFields(
            "total_duration" to totalDuration,
            "load_duration" to loadDuration,
            "prompt_eval_duration" to promptEvalDuration,
            "eval_duration" to evalDuration,
        )
}

private fun reportedFields(vararg fields: Pair<String, Long?>): JsonObject? {
    val present = fields.mapNotNull { (key, value) -> value?.let { key to JsonPrimitive(it) } }
    return if (present.isEmpty()) null else JsonObject(present.toMap())
}

@Serializable
internal data class OllamaListResponse(val models: List<OllamaListEntry> = emptyList())

@Serializable
internal data class OllamaListEntry(val model: String, val name: String? = null)
