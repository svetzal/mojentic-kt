package com.mojentic.examples

import com.mojentic.llm.ImageContent
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.TextContent
import com.mojentic.openai.OpenAIGateway
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Base64

/**
 * Image analysis with a vision-capable OpenAI model.
 *
 * Reads a PNG/JPEG from the first CLI argument, base64-encodes it, and
 * sends it alongside a text prompt to gpt-4o-mini. Requires
 * `OPENAI_API_KEY` to be set.
 */
fun main(args: Array<String>): Unit = runBlocking {
    val imagePath = args.firstOrNull()
        ?: error("Usage: image-analysis <path-to-image>")
    val apiKey = System.getenv("OPENAI_API_KEY")
        ?: error("Set OPENAI_API_KEY to run this example.")

    val file = File(imagePath)
    require(file.isFile) { "$imagePath is not a file" }
    val mime = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> error("Unsupported image extension: ${file.extension}")
    }
    val data = Base64.getEncoder().encodeToString(file.readBytes())

    val gateway = OpenAIGateway(apiKey = apiKey)
    try {
        val broker = LlmBroker(gateway)
        val response = broker.complete(
            model = System.getenv("MOJENTIC_MODEL") ?: "gpt-4o-mini",
            messages = listOf(
                LlmMessage.user(
                    parts = listOf(
                        TextContent("Describe this image in two sentences."),
                        ImageContent(data = data, mimeType = mime),
                    ),
                ),
            ),
        )
        println(response.content)
    } finally {
        gateway.close()
    }
}
