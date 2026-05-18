package com.mojentic.anthropic.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class AnthropicMessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<AnthropicMessage>,
    val system: String? = null,
    val temperature: Double? = null,
    val tools: List<AnthropicTool>? = null,
    @SerialName("tool_choice") val toolChoice: AnthropicToolChoice? = null,
    val stream: Boolean? = null,
)

@Serializable
internal data class AnthropicMessage(
    val role: String,
    val content: List<AnthropicContentBlock>,
)

@Serializable
internal sealed interface AnthropicContentBlock {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : AnthropicContentBlock

    @Serializable
    @SerialName("image")
    data class Image(val source: AnthropicImageSource) : AnthropicContentBlock

    @Serializable
    @SerialName("tool_use")
    data class ToolUse(
        val id: String,
        val name: String,
        val input: JsonObject,
    ) : AnthropicContentBlock

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        @SerialName("tool_use_id") val toolUseId: String,
        val content: String,
        @SerialName("is_error") val isError: Boolean? = null,
    ) : AnthropicContentBlock
}

@Serializable
internal data class AnthropicImageSource(
    val type: String = "base64",
    @SerialName("media_type") val mediaType: String,
    val data: String,
)

@Serializable
internal data class AnthropicTool(
    val name: String,
    val description: String? = null,
    @SerialName("input_schema") val inputSchema: JsonObject,
)

@Serializable
internal data class AnthropicToolChoice(
    val type: String,
    val name: String? = null,
)

@Serializable
internal data class AnthropicMessagesResponse(
    val id: String? = null,
    val model: String? = null,
    val role: String? = null,
    val content: List<AnthropicResponseContent> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: AnthropicUsage? = null,
)

@Serializable
internal sealed interface AnthropicResponseContent {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : AnthropicResponseContent

    @Serializable
    @SerialName("tool_use")
    data class ToolUse(
        val id: String,
        val name: String,
        val input: JsonObject,
    ) : AnthropicResponseContent

    @Serializable
    @SerialName("thinking")
    data class Thinking(val thinking: String) : AnthropicResponseContent
}

@Serializable
internal data class AnthropicUsage(
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
)

@Serializable
internal data class AnthropicModelsListResponse(
    val data: List<AnthropicModelEntry> = emptyList(),
)

@Serializable
internal data class AnthropicModelEntry(val id: String)

@Serializable
internal data class AnthropicErrorResponse(
    val type: String? = null,
    val error: AnthropicErrorBody? = null,
)

@Serializable
internal data class AnthropicErrorBody(
    val type: String? = null,
    val message: String? = null,
)

@Serializable
internal sealed interface AnthropicStreamEvent {
    @Serializable
    @SerialName("message_start")
    data class MessageStart(val message: AnthropicMessagesResponse) : AnthropicStreamEvent

    @Serializable
    @SerialName("content_block_start")
    data class ContentBlockStart(
        val index: Int,
        @SerialName("content_block") val contentBlock: AnthropicResponseContent,
    ) : AnthropicStreamEvent

    @Serializable
    @SerialName("content_block_delta")
    data class ContentBlockDelta(
        val index: Int,
        val delta: AnthropicDelta,
    ) : AnthropicStreamEvent

    @Serializable
    @SerialName("content_block_stop")
    data class ContentBlockStop(val index: Int) : AnthropicStreamEvent

    @Serializable
    @SerialName("message_delta")
    data class MessageDelta(
        val delta: AnthropicMessageStop = AnthropicMessageStop(),
        val usage: AnthropicUsage? = null,
    ) : AnthropicStreamEvent

    @Serializable
    @SerialName("message_stop")
    data object MessageStop : AnthropicStreamEvent

    @Serializable
    @SerialName("ping")
    data object Ping : AnthropicStreamEvent

    @Serializable
    @SerialName("error")
    data class Error(val error: AnthropicErrorBody) : AnthropicStreamEvent
}

@Serializable
internal data class AnthropicMessageStop(
    @SerialName("stop_reason") val stopReason: String? = null,
)

@Serializable
internal sealed interface AnthropicDelta {
    @Serializable
    @SerialName("text_delta")
    data class TextDelta(val text: String) : AnthropicDelta

    @Serializable
    @SerialName("input_json_delta")
    data class InputJsonDelta(@SerialName("partial_json") val partialJson: String) : AnthropicDelta

    @Serializable
    @SerialName("thinking_delta")
    data class ThinkingDelta(val thinking: String) : AnthropicDelta

    @Serializable
    @SerialName("signature_delta")
    data class SignatureDelta(val signature: String) : AnthropicDelta
}

@Serializable
internal data class AnthropicRawEvent(
    val type: String,
    val payload: JsonElement,
)
