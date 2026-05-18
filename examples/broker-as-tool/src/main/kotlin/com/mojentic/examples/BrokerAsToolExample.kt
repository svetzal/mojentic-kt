package com.mojentic.examples

import com.mojentic.agents.BaseAsyncLlmAgent
import com.mojentic.agents.ToolWrapper
import com.mojentic.llm.LlmBroker
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking

/**
 * Demonstrates the "broker as a tool" pattern: a [BaseAsyncLlmAgent] is wrapped
 * via [ToolWrapper] and exposed to a top-level agent as a single-purpose tool.
 *
 * Each wrapped agent runs through its own broker call — the outer LLM never
 * sees the inner system prompt, only the tool's name and description.
 */
fun main(): Unit = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val broker = LlmBroker(gateway)

        val summariser = BaseAsyncLlmAgent(
            broker = broker,
            model = model,
            behaviour = "You produce one-sentence summaries of the input text. Reply with the summary only.",
        )
        val translator = BaseAsyncLlmAgent(
            broker = broker,
            model = model,
            behaviour = "You translate English text into French. Reply with the translation only.",
        )

        val composer = BaseAsyncLlmAgent(
            broker = broker,
            model = model,
            behaviour = "You orchestrate writing tasks by delegating to specialist tools.",
            tools = listOf(
                ToolWrapper(summariser, "summarise", "Produce a one-sentence summary of the given text."),
                ToolWrapper(translator, "translate_to_french", "Translate the given English text into French."),
            ),
        )

        val brief = """
            The Mojentic library is a multi-language agentic framework that provides simple,
            flexible LLM interaction capabilities across Python, TypeScript, Elixir, Rust, and
            now Kotlin. It emphasises a small, composable surface area and works the same way
            across every supported language.
        """.trimIndent()

        val response = composer.generateResponse(
            "Summarise the following text, then translate the summary into French.\n\n$brief",
        )
        println("\n--- composer response ---\n${response.content}")
    } finally {
        gateway.close()
    }
}
