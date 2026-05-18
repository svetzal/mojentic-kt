package com.mojentic.examples

import com.mojentic.agents.SimpleRecursiveAgent
import com.mojentic.agents.SolverEvent
import com.mojentic.llm.LlmBroker
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

/**
 * Demonstrates [SimpleRecursiveAgent]: a recursive solver with a wall-clock
 * timeout that exposes per-iteration events via [SimpleRecursiveAgent.history].
 *
 * Runs two problems concurrently to show the agent is safe to use under
 * structured concurrency.
 */
fun main(): Unit = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val broker = LlmBroker(gateway)

        coroutineScope {
            val problems = listOf(
                "What is the capital of France?",
                "Name the three primary colors.",
            )
            problems.map { problem ->
                async {
                    val agent = SimpleRecursiveAgent(broker, model, maxIterations = 3)
                    val solution = agent.solve(problem, timeoutMs = 60_000L)
                    problem to (solution to agent.history())
                }
            }.awaitAll().forEach { (problem, result) ->
                val (solution, history) = result
                println("\n--- problem: $problem ---")
                println("solution: $solution")
                history.forEach { event ->
                    when (event) {
                        is SolverEvent.GoalSubmitted -> println("  submitted")
                        is SolverEvent.IterationCompleted ->
                            println("  iter ${event.state.iteration}: ${event.response.take(60)}")
                        is SolverEvent.GoalAchieved -> println("  goal achieved at iter ${event.state.iteration}")
                        is SolverEvent.GoalFailed -> println("  goal failed at iter ${event.state.iteration}")
                        is SolverEvent.TimedOut -> println("  timed out")
                    }
                }
            }
        }
    } finally {
        gateway.close()
    }
}
