package com.mojentic.examples

import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking

/**
 * Single-shot completion against a local Ollama. Override `MOJENTIC_MODEL` to
 * select a model already pulled with `ollama pull`.
 */
fun main() = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val broker = LlmBroker(gateway)
        val response = broker.complete(
            model = model,
            messages = listOf(
                LlmMessage.system("You are a haiku poet. Respond in exactly three lines."),
                LlmMessage.user("Write a haiku about Kotlin coroutines."),
            ),
        )
        println(response.content)
    } finally {
        gateway.close()
    }
}
