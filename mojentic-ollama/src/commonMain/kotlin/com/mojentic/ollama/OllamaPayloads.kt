package com.mojentic.ollama

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
internal data class OllamaChatResponse(val model: String? = null, val message: OllamaMessage, val done: Boolean = false)

@Serializable
internal data class OllamaListResponse(val models: List<OllamaListEntry> = emptyList())

@Serializable
internal data class OllamaListEntry(val model: String, val name: String? = null)
