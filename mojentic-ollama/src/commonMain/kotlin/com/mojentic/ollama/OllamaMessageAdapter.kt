package com.mojentic.ollama

import com.mojentic.llm.ImageContent
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.LlmToolCall
import com.mojentic.llm.MessageContent
import com.mojentic.llm.TextContent
import com.mojentic.llm.tools.LlmTool
import kotlinx.serialization.json.Json

internal fun List<LlmMessage>.toOllamaMessages(): List<OllamaMessage> = map { it.toOllamaMessage() }

internal fun LlmMessage.toOllamaMessage(): OllamaMessage {
    val text = content ?: contentParts.orEmpty()
        .filterIsInstance<TextContent>()
        .joinToString(separator = "\n") { it.text }
        .ifEmpty { null }
    val images = contentParts.orEmpty()
        .filterIsInstance<ImageContent>()
        .map { it.data }
        .ifEmpty { null }
    return OllamaMessage(
        role = role.wireValue,
        content = text,
        images = images,
        toolCalls = toolCalls?.map { it.toOllamaToolCall() },
    )
}

internal fun LlmToolCall.toOllamaToolCall(): OllamaToolCall =
    OllamaToolCall(function = OllamaToolFunction(name = name, arguments = arguments))

internal fun OllamaToolCall.toLlmToolCall(): LlmToolCall = LlmToolCall(name = function.name, arguments = function.arguments)

internal fun List<LlmTool>.toOllamaTools(): List<OllamaToolWire> = map { tool ->
    OllamaToolWire(
        function = OllamaToolWireFunction(
            name = tool.descriptor.name,
            description = tool.descriptor.description,
            parameters = tool.descriptor.parameters,
        ),
    )
}

/** Render a [MessageContent] list back to a flat string for providers that don't accept multimodal payloads. */
@Suppress("unused")
internal fun List<MessageContent>.flatText(json: Json = Json.Default): String =
    filterIsInstance<TextContent>().joinToString("\n") { it.text }
