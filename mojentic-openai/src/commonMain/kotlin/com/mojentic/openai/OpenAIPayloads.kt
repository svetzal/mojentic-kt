package com.mojentic.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class OpenAIChatRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    val stream: Boolean = false,
    val tools: List<OpenAITool>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    @SerialName("response_format") val responseFormat: OpenAIResponseFormat? = null,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
)

@Serializable
internal data class OpenAIResponseFormat(
    val type: String,
    @SerialName("json_schema") val jsonSchema: OpenAIJsonSchema? = null,
)

@Serializable
internal data class OpenAIJsonSchema(
    val name: String,
    val schema: JsonObject,
    val strict: Boolean? = null,
)

@Serializable
internal data class OpenAIMessage(
    val role: String,
    val content: JsonElement? = null,
    val name: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenAIToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
)

@Serializable
internal data class OpenAIToolCall(
    val index: Int? = null,
    val id: String? = null,
    val type: String = "function",
    val function: OpenAIToolCallFunction,
)

@Serializable
internal data class OpenAIToolCallFunction(
    val name: String = "",
    val arguments: String = "",
)

@Serializable
internal data class OpenAITool(
    val type: String = "function",
    val function: OpenAIToolFunction,
)

@Serializable
internal data class OpenAIToolFunction(
    val name: String,
    val description: String? = null,
    val parameters: JsonObject,
)

@Serializable
internal data class OpenAIChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<OpenAIChoice> = emptyList(),
)

@Serializable
internal data class OpenAIChoice(
    val index: Int = 0,
    val message: OpenAIResponseMessage? = null,
    val delta: OpenAIResponseMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class OpenAIResponseMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenAIToolCall>? = null,
    val refusal: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
internal data class OpenAIListResponse(val data: List<OpenAIModelEntry> = emptyList())

@Serializable
internal data class OpenAIModelEntry(val id: String)

@Serializable
internal data class OpenAIEmbeddingsRequest(
    val model: String,
    val input: List<String>,
)

@Serializable
internal data class OpenAIEmbeddingsResponse(
    val data: List<OpenAIEmbeddingEntry> = emptyList(),
)

@Serializable
internal data class OpenAIEmbeddingEntry(
    val index: Int = 0,
    val embedding: List<Float> = emptyList(),
)
