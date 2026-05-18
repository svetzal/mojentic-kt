package com.mojentic.examples

import com.mojentic.agents.ReActAgent
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.tools.CurrentDateTimeTool
import com.mojentic.llm.tools.DateResolverTool
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking

/**
 * Demonstrates the [ReActAgent] answering a date-resolution question via the
 * ReAct (Reason + Act) loop. The agent reasons step-by-step, calls date tools
 * as needed, and finishes with a `FINAL ANSWER:` marker.
 */
fun main(): Unit = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val agent = ReActAgent(
            broker = LlmBroker(gateway),
            model = model,
            availableTools = listOf(CurrentDateTimeTool(), DateResolverTool()),
            maxIterations = 5,
        )

        val answer = agent.solve("What date is next Friday? Give the answer in YYYY-MM-DD.")
        println("\n--- final answer ---\n$answer")

        println("\n--- step trace (${agent.steps().size}) ---")
        agent.steps().forEach { step ->
            val tools = if (step.toolCalls.isEmpty()) "" else " [tools: ${step.toolCalls.joinToString()}]"
            println("  ${step.iteration}.${if (step.final) " (final)" else ""}$tools ${step.response.take(80)}")
        }
    } finally {
        gateway.close()
    }
}
