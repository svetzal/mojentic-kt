package com.mojentic.examples

import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.tools.AskUserTool
import com.mojentic.llm.tools.ConsoleUserInteractionGateway
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking

/**
 * Demonstrates the `ask_user` tool: the model decides what it needs to know,
 * the tool blocks on STDIN until the user answers, and the model continues
 * with the answer threaded back through the conversation.
 *
 * Run with a tool-capable Ollama model (`MOJENTIC_MODEL`, default
 * `qwen2.5:7b`).
 */
fun main(): Unit = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val broker = LlmBroker(gateway)
        val tool = AskUserTool(ConsoleUserInteractionGateway())
        val response = broker.complete(
            model = model,
            messages = listOf(
                LlmMessage.system(
                    "You help plan trips. If you need information from the user, " +
                        "use the ask_user tool.",
                ),
                LlmMessage.user("Plan me a one-day visit somewhere fun."),
            ),
            tools = listOf(tool),
        )
        println("\n--- assistant reply ---")
        println(response.content)
    } finally {
        gateway.close()
    }
}
