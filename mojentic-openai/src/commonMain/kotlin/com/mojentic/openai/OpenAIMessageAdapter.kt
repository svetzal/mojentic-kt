package com.mojentic.openai

import com.mojentic.llm.ImageContent
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.LlmToolCall
import com.mojentic.llm.MessageRole
import com.mojentic.llm.TextContent
import com.mojentic.llm.tools.LlmTool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

internal fun List<LlmMessage>.toOpenAIMessages(): List<OpenAIMessage> = flatMap { it.toOpenAIMessages() }

internal fun LlmMessage.toOpenAIMessages(): List<OpenAIMessage> = when (role) {
    MessageRole.Tool -> {
        // OpenAI expects one tool message per tool result. The neutral
        // LlmMessage stores the matching call in `toolCalls[0]`.
        val toolCallId = toolCalls?.firstOrNull()?.id
        listOf(
            OpenAIMessage(
                role = "tool",
                content = JsonPrimitive(content.orEmpty()),
                toolCallId = toolCallId,
            ),
        )
    }
    MessageRole.Assistant -> listOf(
        OpenAIMessage(
            role = "assistant",
            content = content?.let { JsonPrimitive(it) },
            toolCalls = toolCalls?.takeIf { it.isNotEmpty() }?.map { it.toOpenAIToolCall() },
        ),
    )
    else -> listOf(
        OpenAIMessage(
            role = role.wireValue,
            content = openAIContent(),
        ),
    )
}

private fun LlmMessage.openAIContent(): JsonArray? {
    val parts = contentParts ?: return content?.let { JsonArray(listOf(textPart(it))) }
    val mapped = parts.mapNotNull { part ->
        when (part) {
            is TextContent -> textPart(part.text)
            is ImageContent -> imagePart(part)
        }
    }
    return JsonArray(mapped)
}

private fun textPart(text: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("text"))
    put("text", JsonPrimitive(text))
}

private fun imagePart(image: ImageContent): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("image_url"))
    put(
        "image_url",
        buildJsonObject {
            put("url", JsonPrimitive("data:${image.mimeType};base64,${image.data}"))
        },
    )
}

internal fun LlmToolCall.toOpenAIToolCall(): OpenAIToolCall = OpenAIToolCall(
    id = id,
    function = OpenAIToolCallFunction(name = name, arguments = arguments.toString()),
)

internal fun OpenAIToolCall.toLlmToolCall(json: kotlinx.serialization.json.Json): LlmToolCall {
    val parsed = runCatching { json.parseToJsonElement(function.arguments) as? JsonObject }
        .getOrNull()
        ?: buildJsonArgs(function.arguments)
    return LlmToolCall(id = id, name = function.name, arguments = parsed)
}

private fun buildJsonArgs(raw: String): JsonObject = buildJsonObject {
    put("_raw", JsonPrimitive(raw))
}

internal fun List<LlmTool>.toOpenAITools(): List<OpenAITool> = map { tool ->
    OpenAITool(
        function = OpenAIToolFunction(
            name = tool.descriptor.name,
            description = tool.descriptor.description,
            parameters = tool.descriptor.parameters,
        ),
    )
}

@Suppress("unused")
private val MARKER: JsonArray = buildJsonArray { /* keep import path stable */ }
