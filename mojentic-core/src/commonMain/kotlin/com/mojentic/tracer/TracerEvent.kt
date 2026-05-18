package com.mojentic.tracer

import com.mojentic.llm.LlmMessage
import com.mojentic.llm.LlmToolCall
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Closed sum of tracer events recorded by [TracerSystem].
 *
 * Mirrors the Python reference `TracerEvent` hierarchy. Each event carries
 * a wall-clock [timestamp] and a [correlationId] threading the event back
 * to the originating LLM call.
 *
 * Correlation IDs are plain strings (UUID-shaped by convention) so the public
 * surface does not depend on still-experimental `kotlin.uuid.Uuid`.
 */
public sealed interface TracerEvent {
    public val timestamp: Instant
    public val correlationId: String

    /**
     * Human-friendly multi-line summary suitable for printing in examples
     * and demos. Mirrors `tracer_events.py::printable_summary`.
     */
    public fun printableSummary(): String
}

/**
 * Records when an LLM is called with a specific message list and tool set.
 */
public data class LlmCallEvent(
    override val timestamp: Instant = Clock.System.now(),
    override val correlationId: String,
    val model: String,
    val messages: List<LlmMessage>,
    val temperature: Double,
    val tools: List<String>?,
) : TracerEvent {
    override fun printableSummary(): String = buildString {
        append("[$timestamp] LlmCallEvent (correlationId: $correlationId)")
        append("\n   Model: $model")
        if (messages.isNotEmpty()) {
            val count = messages.size
            append("\n   Messages: $count message${if (count == 1) "" else "s"}")
        }
        if (temperature != 1.0) append("\n   Temperature: $temperature")
        if (!tools.isNullOrEmpty()) append("\n   Available Tools: ${tools.joinToString(", ")}")
    }
}

/**
 * Records the response received from an LLM call.
 */
public data class LlmResponseEvent(
    override val timestamp: Instant = Clock.System.now(),
    override val correlationId: String,
    val model: String,
    val content: String?,
    val toolCalls: List<LlmToolCall>?,
    val callDuration: Duration,
) : TracerEvent {
    override fun printableSummary(): String = buildString {
        append("[$timestamp] LlmResponseEvent (correlationId: $correlationId)")
        append("\n   Model: $model")
        if (!content.isNullOrEmpty()) {
            val preview = if (content.length > CONTENT_PREVIEW_MAX) {
                content.take(CONTENT_PREVIEW_MAX) + "..."
            } else {
                content
            }
            append("\n   Content: $preview")
        }
        if (!toolCalls.isNullOrEmpty()) {
            val count = toolCalls.size
            append("\n   Tool Calls: $count call${if (count == 1) "" else "s"}")
        }
        append("\n   Duration: ${callDuration.inWholeMicroseconds / MICROS_PER_MILLI_F} ms")
    }

    private companion object {
        const val CONTENT_PREVIEW_MAX = 100
        const val MICROS_PER_MILLI_F = 1000.0
    }
}

/**
 * Records a single tool invocation.
 */
public data class ToolCallEvent(
    override val timestamp: Instant = Clock.System.now(),
    override val correlationId: String,
    val toolName: String,
    val arguments: String,
    val result: String,
    val callDuration: Duration,
    val isError: Boolean,
    val caller: String? = null,
) : TracerEvent {
    override fun printableSummary(): String = buildString {
        append("[$timestamp] ToolCallEvent (correlationId: $correlationId)")
        append("\n   Tool: $toolName")
        append("\n   Arguments: $arguments")
        val preview = if (result.length > RESULT_PREVIEW_MAX) {
            result.take(RESULT_PREVIEW_MAX) + "..."
        } else {
            result
        }
        append("\n   Result: $preview")
        if (isError) append("\n   isError: true")
        if (caller != null) append("\n   Caller: $caller")
        append("\n   Duration: ${callDuration.inWholeMicroseconds / 1000.0} ms")
    }

    private companion object {
        const val RESULT_PREVIEW_MAX = 100
    }
}

/**
 * Records the end-to-end execution of a parallel tool batch.
 *
 * Per-call detail still lands as [ToolCallEvent] events; the batch event
 * lets observers measure parallelism gains and correlate calls dispatched
 * together. Parity with the Rust and TypeScript ports.
 */
public data class ToolBatchEvent(
    override val timestamp: Instant = Clock.System.now(),
    override val correlationId: String,
    val batchId: String,
    val toolNames: List<String>,
    val successCount: Int,
    val failureCount: Int,
    val callDuration: Duration,
    val caller: String? = null,
) : TracerEvent {
    override fun printableSummary(): String = buildString {
        append("[$timestamp] ToolBatchEvent (correlationId: $correlationId)")
        append("\n   Batch: $batchId")
        append("\n   Tools: ${if (toolNames.isEmpty()) "(none)" else toolNames.joinToString(", ")}")
        append("\n   Outcome: $successCount ok / $failureCount failed")
        append("\n   Duration: ${callDuration.inWholeMicroseconds / 1000.0} ms")
        if (caller != null) append("\n   Caller: $caller")
    }
}

/**
 * Records an inter-agent interaction. Emitted by `Dispatcher` / `Router`
 * once the Phase 4 agent system ships; declared here so consumers can
 * register interest immediately.
 */
public data class AgentInteractionEvent(
    override val timestamp: Instant = Clock.System.now(),
    override val correlationId: String,
    val fromAgent: String,
    val toAgent: String,
    val eventType: String,
    val eventId: String? = null,
) : TracerEvent {
    override fun printableSummary(): String = buildString {
        append("[$timestamp] AgentInteractionEvent (correlationId: $correlationId)")
        append("\n   From: $fromAgent → To: $toAgent")
        append("\n   Event Type: $eventType")
        if (eventId != null) append("\n   Event ID: $eventId")
    }
}
