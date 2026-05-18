package com.mojentic.anthropic.internal

import com.mojentic.llm.ImageContent
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.LlmToolCall
import com.mojentic.llm.MessageRole
import com.mojentic.llm.TextContent
import com.mojentic.llm.tools.LlmTool

/**
 * Translates Mojentic neutral messages into the Anthropic Messages-API shape.
 *
 *  - `System` messages are concatenated and returned via the top-level `system`
 *    field (Anthropic does not accept system messages inside the `messages`
 *    array).
 *  - `User` messages with image parts produce a `content` array of `text` +
 *    `image` blocks. Plain-text user messages produce a single `text` block.
 *  - `Assistant` messages with `toolCalls` produce `tool_use` blocks; text
 *    content (if any) is emitted as a leading `text` block.
 *  - `Tool` messages produce a `user`-role message with a single `tool_result`
 *    block — Anthropic's convention is that the user "returns" the tool call
 *    result.
 */
internal data class AdaptedMessages(
    val system: String?,
    val messages: List<AnthropicMessage>,
)

internal fun List<LlmMessage>.toAnthropicMessages(): AdaptedMessages {
    val systemPieces = filter { it.role == MessageRole.System }
        .mapNotNull { it.content?.takeIf { c -> c.isNotBlank() } }
    val system = if (systemPieces.isEmpty()) null else systemPieces.joinToString(" ")
    val rest = filterNot { it.role == MessageRole.System }
        .map { it.toAnthropicMessage() }
    return AdaptedMessages(system = system, messages = rest)
}

private fun LlmMessage.toAnthropicMessage(): AnthropicMessage = when (role) {
    MessageRole.User -> AnthropicMessage(role = "user", content = userContentBlocks())
    MessageRole.Assistant -> AnthropicMessage(role = "assistant", content = assistantContentBlocks())
    MessageRole.Tool -> AnthropicMessage(role = "user", content = toolContentBlocks())
    MessageRole.System -> error("System messages are pre-extracted before adaptation")
}

private fun LlmMessage.userContentBlocks(): List<AnthropicContentBlock> {
    val parts = contentParts
    if (parts.isNullOrEmpty()) {
        return listOf(AnthropicContentBlock.Text(text = content.orEmpty()))
    }
    return parts.map { part ->
        when (part) {
            is TextContent -> AnthropicContentBlock.Text(text = part.text)
            is ImageContent -> AnthropicContentBlock.Image(
                source = AnthropicImageSource(mediaType = part.mimeType, data = part.data),
            )
        }
    }
}

private fun LlmMessage.assistantContentBlocks(): List<AnthropicContentBlock> {
    val text = content?.takeIf { it.isNotEmpty() }?.let { AnthropicContentBlock.Text(text = it) }
    val toolUses = toolCalls.orEmpty().map { it.toAnthropicToolUse() }
    val blocks = listOfNotNull(text) + toolUses
    return if (blocks.isEmpty()) listOf(AnthropicContentBlock.Text(text = "")) else blocks
}

private fun LlmMessage.toolContentBlocks(): List<AnthropicContentBlock> {
    val matchingCall = toolCalls?.firstOrNull()
    val id = matchingCall?.id
        ?: error("Tool message must carry the matching tool-call id in toolCalls[0].id")
    return listOf(
        AnthropicContentBlock.ToolResult(
            toolUseId = id,
            content = content.orEmpty(),
            isError = null,
        ),
    )
}

private fun LlmToolCall.toAnthropicToolUse(): AnthropicContentBlock.ToolUse =
    AnthropicContentBlock.ToolUse(
        id = id ?: error("Anthropic tool_use requires a non-null call id"),
        name = name,
        input = arguments,
    )

internal fun List<LlmTool>.toAnthropicTools(): List<AnthropicTool> = map { tool ->
    AnthropicTool(
        name = tool.descriptor.name,
        description = tool.descriptor.description,
        inputSchema = tool.descriptor.parameters,
    )
}
