package com.mojentic.examples

import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.tools.ConsoleUserInteractionGateway
import com.mojentic.llm.tools.TellUserTool
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking

/**
 * Demonstrates the `tell_user` tool: the model emits intermediate updates
 * to the user while it works on a multi-step task.
 */
fun main(): Unit = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val broker = LlmBroker(gateway)
        val tool = TellUserTool(ConsoleUserInteractionGateway())
        val response = broker.complete(
            model = model,
            messages = listOf(
                LlmMessage.system(
                    "You think aloud while solving problems. Use the tell_user tool to " +
                        "share notable observations with the user before delivering the " +
                        "final answer.",
                ),
                LlmMessage.user("List three reasons trains are nicer than planes."),
            ),
            tools = listOf(tool),
        )
        println("\n--- final assistant reply ---")
        println(response.content)
    } finally {
        gateway.close()
    }
}
