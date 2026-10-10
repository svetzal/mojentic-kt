package com.mojentic.llm.recovery

import com.mojentic.llm.CompletionEvidence
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Validated provider stream could not complete. Details remain private to explicit cause inspection. */
public class RecoveryStreamException(private val evidence: Any? = null) : RuntimeException("Incomplete provider stream") {
    /** May contain provider content; callers own privacy when inspecting this value. */
    public fun inspectEvidence(): Any? = evidence
}

/** EOF is transport evidence only; it does not prove remote inference ended. */
public class RecoveryStreamClosedException : IOException("Stream closed without terminal evidence")

/** Owned incremental response. Observation and exact capture precede any downstream delivery. */
public class RecoveryStream internal constructor(
    private val channel: ByteReadChannel,
    private val evidence: ResponseEvidence,
    private val identity: RecoveryIdentity,
    private val policy: RecoveryPolicy,
    private val payload: String,
) {
    private val pending = mutableListOf<Byte>()
    private var ended = false
    private var frameIndex = 0L

    /** Reads a UTF-8 line without rewriting the captured HTTP body bytes. */
    public suspend fun readLine(): String? {
        while (true) {
            currentCoroutineContext().ensureActive()
            val newline = pending.indexOf('\n'.code.toByte())
            if (newline >= 0) {
                val line = decode(pending.take(newline).toByteArray()).removeSuffix("\r")
                repeat(newline + 1) { pending.removeAt(0) }
                return line
            }
            if (ended) {
                if (pending.isEmpty()) return null
                val line = decode(pending.toByteArray())
                pending.clear()
                return line
            }
            val buffer = ByteArray(BUFFER_SIZE)
            val count = channel.readAvailable(buffer)
            if (count < 0) {
                channel.closedCause?.let { throw it }
                ended = true
            } else if (count > 0) {
                val bytes = buffer.copyOf(count)
                evidence.bytes.addAll(bytes.toList())
                pending.addAll(bytes.toList())
                val previous = evidence.streamProgress
                val observed = streamingProgress(evidence.bytes.toByteArray())
                evidence.streamProgress = observed.copy(
                    completedToolCallsObserved = previous.completedToolCallsObserved,
                    observed = observed.observed.copy(completedToolCalls = previous.observed.completedToolCalls),
                )
                evidence.captureResponse(policy, identity, payload, complete = false)
            }
        }
    }

    /** Marks a direct delivery after cancellation checks, before entering a suspending collector. */
    public suspend fun delivered(content: String? = null, reasoning: String? = null, toolCalls: Int = 0) {
        currentCoroutineContext().ensureActive()
        evidence.delivered = true
        val previous = evidence.deliveredCounts
        evidence.deliveredCounts = previous.copy(
            contentBytes = previous.contentBytes + (content?.encodeToByteArray()?.size ?: 0),
            reasoningBytes = previous.reasoningBytes + (reasoning?.encodeToByteArray()?.size ?: 0),
            completedToolCalls = previous.completedToolCalls + toolCalls,
        )
    }

    /** Capture completion before emitting successful terminal evidence or completed tools. */
    public suspend fun finish() {
        currentCoroutineContext().ensureActive()
        evidence.captureResponse(policy, identity, payload, complete = true)
        currentCoroutineContext().ensureActive()
    }

    /** Retain validated completion telemetry privately, including when a later read or capture fails. */
    public fun completionEvidence(completion: CompletionEvidence) {
        evidence.completion = completion
    }

    /** Accept only numeric counts and durations under documented provider keys. */
    public fun metrics(usage: JsonObject?, durations: JsonObject?) {
        val metrics = RecoveryStreamMetrics(numbers(usage, USAGE_KEYS), numbers(durations, DURATION_KEYS))
        observe(RecoveryStage.METRICS, metrics)
    }

    /** Safe validated progress observation, with actual wire identity and one-based frame index. */
    public fun progress(completedTools: Int = 0) {
        frameIndex++
        completedTools(completedTools)
        observe(RecoveryStage.PROGRESS)
    }

    /** Records validated completed calls before final capture or delivery. */
    public fun completedTools(count: Int) {
        if (count > 0) {
            evidence.streamProgress = evidence.streamProgress.copy(
                completedToolCallsObserved = count,
                observed = evidence.streamProgress.observed.copy(completedToolCalls = count.toLong()),
            )
        }
    }

    /** Marks decoding failures without replacing the original typed cause. */
    public fun invalid(cause: Throwable): Nothing {
        evidence.decoding = true
        throw cause
    }

    private fun observe(stage: RecoveryStage, metrics: RecoveryStreamMetrics? = null) {
        evidence.observation = true
        policy.observer(
            RecoveryEvent(
                stage,
                identity,
                wireAttempts = identity.attemptNumber,
                progress = evidence.progress(),
                frameIndex = frameIndex,
                metrics = metrics,
            ),
        )
        evidence.observation = false
    }

    private fun decode(bytes: ByteArray): String = try {
        bytes.decodeToString(throwOnInvalidSequence = true)
    } catch (cause: Exception) {
        invalid(cause)
    }

    private fun numbers(values: JsonObject?, keys: Set<String>): Map<String, Long> = values.orEmpty().mapNotNull { (key, value) ->
        val number = (value as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
        if (key in keys && number != null && number >= 0) key to number else null
    }.toMap()

    private companion object {
        const val BUFFER_SIZE = 8192
        val USAGE_KEYS = setOf("prompt_eval_count", "eval_count", "prompt_tokens", "completion_tokens", "total_tokens")
        val DURATION_KEYS = setOf("total_duration", "load_duration", "prompt_eval_duration", "eval_duration")
    }
}
