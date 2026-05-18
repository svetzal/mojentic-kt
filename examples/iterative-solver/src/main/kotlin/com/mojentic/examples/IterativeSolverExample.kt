package com.mojentic.examples

import com.mojentic.agents.IterativeProblemSolver
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.tools.CurrentDateTimeTool
import com.mojentic.llm.tools.DateResolverTool
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking

/**
 * Demonstrates [IterativeProblemSolver] with a date-resolution toolkit.
 *
 * The solver iterates up to three times, asking the LLM to commit to `DONE`
 * once the answer is known. The Phase-3 date tools are passed in to show how
 * the broker dispatches tool calls between the LLM and the loop.
 */
fun main(): Unit = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val solver = IterativeProblemSolver(
            broker = LlmBroker(gateway),
            model = model,
            availableTools = listOf(CurrentDateTimeTool(), DateResolverTool()),
        )

        val problem = "What date is the second Tuesday of next month? Give the answer in YYYY-MM-DD."
        val answer = solver.solve(problem)
        println("\n--- final answer ---\n$answer")
    } finally {
        gateway.close()
    }
}
