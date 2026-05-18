package com.mojentic.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Role of a message in an LLM conversation.
 *
 * Wire values are lowercase strings matching the OpenAI / Ollama conventions.
 */
@Serializable
public enum class MessageRole {
    System,
    User,
    Assistant,
    Tool,
    ;

    public val wireValue: String
        get() = name.lowercase()
}

/**
 * A typed content part of a message. `LlmMessage` carries either plain text
 * via [LlmMessage.content], or — when richer payloads are needed — a list of
 * [MessageContent] parts via [LlmMessage.contentParts].
 */
@Serializable
public sealed interface MessageContent

@Serializable
public data class TextContent(val text: String) : MessageContent

/**
 * Base64-encoded image content. [mimeType] follows IANA naming, e.g.
 * `image/png`, `image/jpeg`.
 */
@Serializable
public data class ImageContent(val data: String, val mimeType: String) : MessageContent

/**
 * A single tool-call request emitted by the LLM.
 *
 * @property id Provider-supplied call identifier, when present.
 * @property name Tool name as the LLM addressed it.
 * @property arguments Parsed JSON arguments object. Use the kotlinx.serialization
 *           `JsonObject` directly to avoid imposing a fixed value type.
 */
@Serializable
public data class LlmToolCall(val id: String? = null, val name: String, val arguments: JsonObject)

/**
 * A message in an LLM conversation. Accumulates during a chat session.
 *
 * Use the companion factories ([system], [user], [assistant], [tool]) for the
 * common shapes; construct directly for advanced cases.
 *
 * @property role Speaker role.
 * @property content Plain text content, when the message is a single text part.
 * @property contentParts Rich content parts (text + images), when present.
 * @property toolCalls Tool-call requests issued by an assistant message, or
 *           the call to which a tool message is responding.
 */
@Serializable
public data class LlmMessage(
    val role: MessageRole = MessageRole.User,
    val content: String? = null,
    val contentParts: List<MessageContent>? = null,
    val toolCalls: List<LlmToolCall>? = null,
) {
    public companion object {
        public fun system(content: String): LlmMessage = LlmMessage(role = MessageRole.System, content = content)

        public fun user(content: String): LlmMessage = LlmMessage(role = MessageRole.User, content = content)

        public fun user(parts: List<MessageContent>): LlmMessage = LlmMessage(role = MessageRole.User, contentParts = parts)

        public fun assistant(content: String? = null, toolCalls: List<LlmToolCall>? = null): LlmMessage =
            LlmMessage(role = MessageRole.Assistant, content = content, toolCalls = toolCalls)

        public fun tool(content: String, toolCall: LlmToolCall): LlmMessage =
            LlmMessage(role = MessageRole.Tool, content = content, toolCalls = listOf(toolCall))
    }
}

/**
 * A non-streaming response from an [com.mojentic.llm.gateway.LlmGateway].
 *
 * @property content Assistant text response, when present.
 * @property toolCalls Tool calls requested by the LLM. Empty when no tools were called.
 * @property thinking Reasoning trace surfaced by the provider, when available.
 * @property structuredJson Structured JSON object, when a structured-output request was made.
 */
public data class LlmGatewayResponse(
    val content: String? = null,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val thinking: String? = null,
    val structuredJson: JsonElement? = null,
)
