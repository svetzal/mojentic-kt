package com.mojentic.llm.tools

import com.mojentic.llm.LlmToolCall
import com.mojentic.tracer.NullTracer
import com.mojentic.tracer.Tracer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
 * At most [maxConcurrency] calls execute at once; outcomes keep request
 * order. A call naming an unknown tool yields an error outcome in its slot.
 *
 * Cancellation propagates cooperatively: cancelling the calling coroutine
 * cancels every in-flight child via `coroutineScope`.
 *
 * @throws IllegalArgumentException if [maxConcurrency] is not positive.
 */
@OptIn(ExperimentalUuidApi::class)
public class ParallelToolRunner(
    private val tracer: Tracer = NullTracer,
    private val caller: String? = null,
    private val maxConcurrency: Int = 4,
) : ToolRunner {
    init {
        require(maxConcurrency > 0) { "maxConcurrency must be positive" }
    }

    override suspend fun runBatch(
        calls: List<LlmToolCall>,
        tools: List<LlmTool>,
        correlationId: String?,
    ): List<ToolOutcome> {
        if (calls.isEmpty()) return emptyList()

        val batchId = Uuid.random().toString()
        val mark = TimeSource.Monotonic.markNow()
        val semaphore = Semaphore(maxConcurrency)
        val outcomes = coroutineScope {
            calls.map { call ->
                async {
                    semaphore.withPermit {
                        val tool = tools.firstOrNull { it.matches(call.name) }
                        if (tool == null) missingToolOutcome(call) else runOne(call, tool)
                    }
                }
            }.awaitAll()
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
