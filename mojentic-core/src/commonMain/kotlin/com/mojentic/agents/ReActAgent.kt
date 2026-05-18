package com.mojentic.agents

import com.mojentic.llm.ChatSession
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.tools.LlmTool
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * One iteration of a [ReActAgent] solve loop.
 *
 * Captures the assistant's textual response together with whatever tool calls
 * the broker resolved on that turn (when any). [final] is true on the
 * iteration that produced a `FINAL ANSWER:` marker.
 */
public data class ReActStep(
    val iteration: Int,
    val response: String,
    val toolCalls: List<String>,
    val final: Boolean,
)

/**
 * Single-class reasoning-and-acting (ReAct) agent.
 *
 * Runs a chat loop with a system prompt that asks the LLM to alternate
 * `Thought:` / `Action:` / `Observation:` cycles and finish with a
 * `FINAL ANSWER:` marker. The underlying [LlmBroker] handles tool dispatch
 * recursively, so each iteration here is one round-trip of *reasoning* — the
 * tool plumbing is already taken care of.
 *
 * Mirrors the Python reference's ReAct example pattern, collapsed into a
 * single Kotlin class. The multi-agent fan-out of the Python example is
 * intentionally not ported — the broker's recursive tool execution is the
 * Kotlin idiom for the same loop.
 */
public class ReActAgent(
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
        systemPrompt = systemPrompt ?: DEFAULT_SYSTEM_PROMPT,
        tools = availableTools,
        config = config,
    )

    private val emitted: MutableList<ReActStep> = mutableListOf()

    /** Snapshot of every [ReActStep] emitted during the most recent [solve] call. */
    public fun steps(): List<ReActStep> = emitted.toList()

    /**
     * Run the ReAct loop until the model emits a `FINAL ANSWER:` line or the
     * iteration budget is exhausted. Returns the extracted final answer (or
     * the last raw response when the budget runs out).
     */
    public suspend fun solve(query: String): String {
        emitted.clear()
        var iteration = 0
        var lastContent = ""
        while (iteration < maxIterations) {
            iteration += 1
            val prompt = if (iteration == 1) {
                """
                |User Query: $query
                |
                |Reason step by step using the ReAct pattern. Invoke any tools you need.
                |When you have the answer, write a final line:
                |FINAL ANSWER: <your answer>
                """.trimMargin()
            } else {
                """
                |Continue.
                |If you can now answer, write a final line: FINAL ANSWER: <your answer>
                |Otherwise, perform the next Thought / Action / Observation step.
                """.trimMargin()
            }
            val response = chat.send(prompt)
            val content = response.content.orEmpty()
            lastContent = content
            val isFinal = FINAL_ANSWER_REGEX.containsMatchIn(content)
            emitted += ReActStep(
                iteration = iteration,
                response = content,
                toolCalls = response.toolCalls.map { it.name },
                final = isFinal,
            )
            if (isFinal) {
                return extractFinalAnswer(content)
            }
        }
        logger.info { "ReActAgent budget exhausted after $maxIterations iterations." }
        return lastContent
    }

    private fun extractFinalAnswer(content: String): String {
        val match = FINAL_ANSWER_REGEX.find(content) ?: return content
        val tail = content.substring(match.range.last + 1).trim()
        return tail.ifEmpty { content.trim() }
    }

    public companion object {
        private val FINAL_ANSWER_REGEX: Regex = Regex("(?i)final\\s+answer:")

        public const val DEFAULT_SYSTEM_PROMPT: String =
            """You operate using the ReAct (Reason + Act) pattern.
For each step you take, follow this format:

Thought: <reason about the problem and decide what to do>
Action: <call a tool if you need information, or note that you are ready to answer>
Observation: <reflect on the tool's output, when applicable>

When you have enough information to answer, finish with a single line:
FINAL ANSWER: <your answer>

Be concise. Do not invent facts. Only use tools that are available to you."""
    }
}
