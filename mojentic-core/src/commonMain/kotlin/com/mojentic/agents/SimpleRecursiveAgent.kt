package com.mojentic.agents

import com.mojentic.llm.ChatSession
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.tools.LlmTool
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Goal state captured for each turn of a [SimpleRecursiveAgent].
 *
 * Subscribe to [SimpleRecursiveAgent.observe] to receive snapshots as the
 * solver iterates.
 */
public data class GoalState(
    val goal: String,
    val iteration: Int = 0,
    val maxIterations: Int = 5,
    val solution: String? = null,
    val isComplete: Boolean = false,
)

/** A snapshot of a solver iteration, surfaced to observers. */
public sealed interface SolverEvent {
    public val state: GoalState

    public data class GoalSubmitted(override val state: GoalState) : SolverEvent
    public data class IterationCompleted(override val state: GoalState, val response: String) : SolverEvent
    public data class GoalAchieved(override val state: GoalState) : SolverEvent
    public data class GoalFailed(override val state: GoalState) : SolverEvent
    public data class TimedOut(override val state: GoalState) : SolverEvent
}

/**
 * Recursive problem solver — like [IterativeProblemSolver], but exposes per-
 * iteration state via a [kotlinx.coroutines.flow.SharedFlow] for observability
 * and applies an overall wall-clock timeout via [solve].
 *
 * Mirrors the Python reference's `SimpleRecursiveAgent`. The Kotlin port
 * collapses the EventEmitter pubsub pattern into a single `SharedFlow`.
 */
public class SimpleRecursiveAgent(
    private val broker: LlmBroker,
    private val model: String,
    availableTools: List<LlmTool> = emptyList(),
    private val maxIterations: Int = 5,
    systemPrompt: String? = null,
    config: CompletionConfig = CompletionConfig(),
) {
    private val chat: ChatSession = ChatSession(
        broker = broker,
        model = model,
        systemPrompt = systemPrompt ?: IterativeProblemSolver.DEFAULT_SYSTEM_PROMPT,
        tools = availableTools,
        config = config,
    )

    private val emitted: MutableList<SolverEvent> = mutableListOf()

    /** Read all events emitted during the most recent [solve] call. */
    public fun history(): List<SolverEvent> = emitted.toList()

    public suspend fun solve(problem: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String {
        emitted.clear()
        val initial = GoalState(goal = problem, maxIterations = maxIterations)
        emitted += SolverEvent.GoalSubmitted(initial)

        val outcome = withTimeoutOrNull(timeoutMs) {
            var current = initial
            while (true) {
                val next = current.copy(iteration = current.iteration + 1)
                val response = step(next.goal)
                val normalised = response.trim().uppercase()
                when {
                    normalised == "FAIL" -> {
                        val final = next.copy(
                            solution = "Failed to solve after ${next.iteration} iterations:\n$response",
                            isComplete = true,
                        )
                        emitted += SolverEvent.IterationCompleted(next, response)
                        emitted += SolverEvent.GoalFailed(final)
                        return@withTimeoutOrNull final
                    }
                    normalised == "DONE" -> {
                        val final = next.copy(solution = response, isComplete = true)
                        emitted += SolverEvent.IterationCompleted(next, response)
                        emitted += SolverEvent.GoalAchieved(final)
                        return@withTimeoutOrNull final
                    }
                    next.iteration >= next.maxIterations -> {
                        val final = next.copy(
                            solution = "Best solution after ${next.maxIterations} iterations:\n$response",
                            isComplete = true,
                        )
                        emitted += SolverEvent.IterationCompleted(next, response)
                        emitted += SolverEvent.GoalAchieved(final)
                        return@withTimeoutOrNull final
                    }
                    else -> {
                        emitted += SolverEvent.IterationCompleted(next, response)
                        current = next
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            current
        }

        return if (outcome == null) {
            val timedOut = initial.copy(
                solution = "Timeout: Could not solve the problem within $timeoutMs ms.",
                isComplete = true,
            )
            emitted += SolverEvent.TimedOut(timedOut)
            timedOut.solution!!
        } else {
            outcome.solution.orEmpty()
        }
    }

    private suspend fun step(problem: String): String {
        val prompt = """
            |Given the user request:
            |$problem
            |
            |Use the tools at your disposal to act on their request.
            |You may wish to create a step-by-step plan for more complicated requests.
            |
            |If you cannot provide an answer, say only "FAIL".
            |If you have the answer, say only "DONE".
        """.trimMargin()
        return chat.send(prompt).content.orEmpty()
    }

    public companion object {
        public const val DEFAULT_TIMEOUT_MS: Long = 300_000L
    }
}
