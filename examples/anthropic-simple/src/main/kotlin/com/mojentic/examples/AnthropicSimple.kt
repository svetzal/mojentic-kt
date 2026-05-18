package com.mojentic.examples

import com.mojentic.anthropic.AnthropicGateway
import com.mojentic.llm.LlmMessage
import kotlinx.coroutines.runBlocking

/**
 * Sends a single prompt to Anthropic's Messages API and prints the assistant's reply.
 *
 * Requires:
 *  - `ANTHROPIC_API_KEY` environment variable.
 *  - `MOJENTIC_ANTHROPIC_MODEL` (defaults to `claude-3-5-sonnet-latest`).
 */
fun main(): Unit = runBlocking {
    val apiKey = System.getenv("ANTHROPIC_API_KEY")
        ?: error("Set ANTHROPIC_API_KEY to run this example")
    val model = System.getenv("MOJENTIC_ANTHROPIC_MODEL") ?: "claude-3-5-sonnet-latest"

    val gateway = AnthropicGateway(apiKey = apiKey)
    try {
        val response = gateway.complete(
            model = model,
            messages = listOf(
                LlmMessage.system("You are a concise assistant. Reply in one sentence."),
                LlmMessage.user("Give me one fun fact about octopuses."),
            ),
        )
        println(response.content ?: "[no content]")
        response.thinking?.let { println("---\n[thinking]\n$it") }
    } finally {
        gateway.close()
    }
}
