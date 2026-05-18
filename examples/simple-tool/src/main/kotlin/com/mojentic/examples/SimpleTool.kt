package com.mojentic.examples

import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.tools.DateResolverTool
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking

/**
 * Demonstrates a single tool call: the model invokes `resolve_date` to map
 * a relative date to an ISO-8601 value.
 */
fun main() = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val broker = LlmBroker(gateway)
        val response = broker.complete(
            model = model,
            messages = listOf(
                LlmMessage.system("Use the resolve_date tool when the user mentions a relative date."),
                LlmMessage.user("What's the date 3 days from today?"),
            ),
            tools = listOf(DateResolverTool()),
        )
        println(response.content)
    } finally {
        gateway.close()
    }
}
