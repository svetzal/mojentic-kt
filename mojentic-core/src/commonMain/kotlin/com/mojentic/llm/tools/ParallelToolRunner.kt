package com.mojentic.llm.tools

import com.mojentic.llm.LlmToolCall
import com.mojentic.tracer.NullTracer
import com.mojentic.tracer.Tracer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.time.TimeSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Parallel tool runner — fans out tool calls onto child coroutines and waits
 * for all of them. Opt-in alternative to [SerialToolRunner].
 *
 * Per-call wall-clock duration still lands on each [ToolOutcome]; the
 * runner additionally emits a single [com.mojentic.tracer.ToolBatchEvent]
 * via [tracer] summarising the batch (size, success / failure counts,
 * aggregate latency) so observers can quantify parallelism gains.
 *
 * Cancellation propagates cooperatively: cancelling the calling coroutine
 * cancels every in-flight child via `coroutineScope`.
 */
@OptIn(ExperimentalUuidApi::class)
public class ParallelToolRunner(
    private val tracer: Tracer = NullTracer,
    private val caller: String? = null,
) : ToolRunner {

    override suspend fun runBatch(
        calls: List<LlmToolCall>,
        tools: List<LlmTool>,
        correlationId: String?,
    ): List<ToolOutcome> {
        val known = calls.mapNotNull { call ->
            val tool = tools.firstOrNull { it.matches(call.name) } ?: return@mapNotNull null
            call to tool
        }
        if (known.isEmpty()) return emptyList()

        val batchId = Uuid.random().toString()
        val mark = TimeSource.Monotonic.markNow()
        val outcomes = coroutineScope {
            known.map { (call, tool) -> async { runOne(call, tool) } }.awaitAll()
        }
        val batchDuration = mark.elapsedNow()
        val (ok, failed) = outcomes.partition { it.isOk }
        tracer.recordToolBatch(
            batchId = batchId,
            toolNames = outcomes.map { it.call.name },
            successCount = ok.size,
            failureCount = failed.size,
            callDuration = batchDuration,
            correlationId = correlationId,
            caller = caller,
        )
        return outcomes
    }

    private suspend fun runOne(call: LlmToolCall, tool: LlmTool): ToolOutcome {
        val mark = TimeSource.Monotonic.markNow()
        return try {
            val result = tool.execute(call.arguments)
            ToolOutcome(call = call, result = result, duration = mark.elapsedNow())
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (failure: Throwable) {
            ToolOutcome(call = call, error = failure, duration = mark.elapsedNow())
        }
    }
}
