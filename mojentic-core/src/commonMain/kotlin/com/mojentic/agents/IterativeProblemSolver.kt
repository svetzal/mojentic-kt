package com.mojentic.agents

import com.mojentic.llm.ChatSession
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.tools.LlmTool
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Loop-based problem solver backed by a [ChatSession].
 *
 * Calls the LLM up to [maxIterations] times. Each turn instructs the LLM to
 * use available tools and reply with only `DONE` (success) or `FAIL`
 * (give up). The loop stops on either signal — or when the iteration budget
 * is exhausted — then asks for a final summary turn.
 *
 * Mirrors the Python reference's `IterativeProblemSolver`.
 */
public class IterativeProblemSolver(
    private val broker: LlmBroker,
    private val model: String,
    private val availableTools: List<LlmTool> = emptyList(),
    private val maxIterations: Int = 3,
    systemPrompt: String? = null,
    config: CompletionConfig = CompletionConfig(),
) {
    private val chat: ChatSession = ChatSession(
        broker = broker,
        model = model,
        systemPrompt = systemPrompt ?: DEFAULT_SYSTEM_PROMPT,
        tools = availableTools,
        config = config,
    )

    public suspend fun solve(problem: String): String {
        var remaining = maxIterations
        while (true) {
            val result = step(problem).orEmpty()
            val normalised = result.uppercase()
            when {
                "FAIL" in normalised -> {
                    logger.info { "Task failed: $result" }
                    break
                }
                "DONE" in normalised -> {
                    logger.info { "Task completed: $result" }
                    break
                }
                else -> {
                    remaining -= 1
                    if (remaining <= 0) {
                        logger.info { "Max iterations reached" }
                        break
                    }
                }
            }
        }
        val summary = chat.send(
            "Summarize the final result, and only the final result, " +
                "without commenting on the process by which you achieved it.",
        )
        return summary.content.orEmpty()
    }

    private suspend fun step(problem: String): String? {
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
        return chat.send(prompt).content
    }

    public companion object {
        public const val DEFAULT_SYSTEM_PROMPT: String =
            "You are a problem-solving assistant that can solve complex problems step by step. " +
                "You analyze problems, break them down into smaller parts, " +
                "and solve them systematically. " +
                "If you cannot solve a problem completely in one step, " +
                "you make progress and identify what to do next."
    }
}
