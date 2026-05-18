package com.mojentic.examples

import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.StreamEvent
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

/**
 * Demonstrates streaming completion. Text chunks are printed as they arrive.
 */
fun main() = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val broker = LlmBroker(gateway)
        broker
            .stream(
                model = model,
                messages = listOf(
                    LlmMessage.system("Be brief."),
                    LlmMessage.user("Explain coroutines in two sentences."),
                ),
            )
            .collect { event ->
                if (event is StreamEvent.TextChunk) print(event.text)
            }
        println()
    } finally {
        gateway.close()
    }
}
