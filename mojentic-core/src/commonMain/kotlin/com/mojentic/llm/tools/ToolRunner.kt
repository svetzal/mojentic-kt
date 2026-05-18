package com.mojentic.llm.tools

import com.mojentic.llm.LlmToolCall
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * The outcome of executing a single tool call.
 *
 * @property call The tool call as the LLM requested it.
 * @property result Serialised tool result (already JSON-encoded by the tool).
 * @property error Failure cause, when execution did not succeed.
 * @property duration Wall-clock duration of the tool call.
 */
public data class ToolOutcome(
    val call: LlmToolCall,
    val result: String? = null,
    val error: Throwable? = null,
    val duration: Duration,
) {
    public val isOk: Boolean
        get() = error == null
}

/**
 * Strategy for executing a batch of tool calls returned by the LLM.
 *
 * Implementations are `suspend` so concurrency strategy is decided at the
 * implementation layer. [SerialToolRunner] is the broker default;
 * [ParallelToolRunner] is opt-in.
 */
public interface ToolRunner {
    /**
     * Execute [calls] against [tools], returning outcomes in input order.
     *
     * Calls whose `name` matches no tool in [tools] are skipped silently
     * (warned via the caller's logger upstream).
     *
     * [correlationId] threads through to any tracer events the runner
     * emits (e.g. [ParallelToolRunner]'s batch event).
     */
    public suspend fun runBatch(
        calls: List<LlmToolCall>,
        tools: List<LlmTool>,
        correlationId: String? = null,
    ): List<ToolOutcome>
}

/**
 * Serial tool runner — executes tool calls one after another in input order.
 *
 * This preserves predictable conversation history and is the default for
 * [com.mojentic.llm.LlmBroker].
 */
public class SerialToolRunner : ToolRunner {
    override suspend fun runBatch(
        calls: List<LlmToolCall>,
        tools: List<LlmTool>,
        correlationId: String?,
    ): List<ToolOutcome> {
        val outcomes = mutableListOf<ToolOutcome>()
        for (call in calls) {
            val tool = tools.firstOrNull { it.matches(call.name) } ?: continue
            outcomes += runOne(call, tool)
        }
        return outcomes
    }

    private suspend fun runOne(call: LlmToolCall, tool: LlmTool): ToolOutcome {
        val mark = TimeSource.Monotonic.markNow()
        return try {
            val result = tool.execute(call.arguments)
            ToolOutcome(call = call, result = result, duration = mark.elapsedNow())
        } catch (cancel: TimeoutCancellationException) {
            throw cancel
        } catch (failure: Throwable) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            ToolOutcome(call = call, error = failure, duration = mark.elapsedNow())
        }
    }
}
