package com.mojentic.examples

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmGateway
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.ReasoningEffort
import com.mojentic.ollama.OllamaGateway
import com.mojentic.openai.OpenAIGateway
import kotlinx.coroutines.runBlocking

/**
 * Three short broker recipes back-to-back, showing how to swap providers
 * behind the same [LlmBroker] surface. Run with `MOJENTIC_PROVIDER=openai`
 * to drive OpenAI (requires `OPENAI_API_KEY`); defaults to Ollama.
 */
fun main(): Unit = runBlocking {
    val provider = System.getenv("MOJENTIC_PROVIDER") ?: "ollama"
    val gateway: LlmGateway = when (provider) {
        "openai" -> OpenAIGateway(apiKey = requireKey("OPENAI_API_KEY"))
        else -> OllamaGateway()
    }
    val model = System.getenv("MOJENTIC_MODEL") ?: defaultModel(provider)
    val broker = LlmBroker(gateway)

    try {
        println("--- 1. one-shot ---")
        val one = broker.complete(model = model, messages = listOf(LlmMessage.user("Reply with 'pong'.")))
        println(one.content)

        println("\n--- 2. with system prompt ---")
        val two = broker.complete(
            model = model,
            messages = listOf(
                LlmMessage.system("Always reply in haiku."),
                LlmMessage.user("Describe a quiet morning."),
            ),
            config = CompletionConfig(temperature = 0.7),
        )
        println(two.content)

        if (provider == "openai") {
            println("\n--- 3. with reasoning effort (o-series) ---")
            val three = broker.complete(
                model = System.getenv("MOJENTIC_REASONING_MODEL") ?: "o3-mini",
                messages = listOf(LlmMessage.user("What is 17 times 23? Show your reasoning briefly.")),
                config = CompletionConfig(reasoningEffort = ReasoningEffort.LOW),
            )
            println(three.content)
        }
    } finally {
        when (gateway) {
            is OllamaGateway -> gateway.close()
            is OpenAIGateway -> gateway.close()
        }
    }
}

private fun defaultModel(provider: String): String = when (provider) {
    "openai" -> "gpt-4o-mini"
    else -> "qwen2.5:7b"
}

private fun requireKey(name: String): String =
    System.getenv(name) ?: error("Set $name to run this example.")
