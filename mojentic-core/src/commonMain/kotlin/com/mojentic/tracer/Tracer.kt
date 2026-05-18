package com.mojentic.tracer

import com.mojentic.llm.LlmMessage
import com.mojentic.llm.LlmToolCall
import kotlin.time.Duration

/**
 * Observability hook surface called by [com.mojentic.llm.LlmBroker] and the
 * tool runners.
 *
 * Every method has a no-op default so implementations only override what
 * they care about. [NullTracer] inherits all defaults; [TracerSystem]
 * forwards every call to its [EventStore].
 *
 * Methods are `suspend` because [TracerSystem] performs a `Mutex`-protected
 * append; the broker calls every recorder from a suspend context so this
 * imposes no extra burden.
 *
 * Correlation IDs are opaque strings (UUID-shaped by convention) so the
 * public surface does not depend on still-experimental `kotlin.uuid.Uuid`.
 */
public interface Tracer {
    public suspend fun recordLlmCall(
        model: String,
        messages: List<LlmMessage>,
        temperature: Double,
        tools: List<String>?,
        correlationId: String?,
    ) {
    }

    public suspend fun recordLlmResponse(
        model: String,
        content: String?,
        toolCalls: List<LlmToolCall>?,
        callDuration: Duration,
        correlationId: String?,
    ) {
    }

    public suspend fun recordToolCall(
        toolName: String,
        arguments: String,
        result: String,
        callDuration: Duration,
        isError: Boolean,
        correlationId: String?,
        caller: String? = null,
    ) {
    }

    /**
     * Emitted once per parallel tool batch in addition to the per-call
     * [recordToolCall] events. Serial runners do not emit batch events.
     */
    public suspend fun recordToolBatch(
        batchId: String,
        toolNames: List<String>,
        successCount: Int,
        failureCount: Int,
        callDuration: Duration,
        correlationId: String?,
        caller: String? = null,
    ) {
    }

    /**
     * Emitted by `Dispatcher` / `Router` when the Phase 4 agent system
     * routes an event between agents.
     */
    public suspend fun recordAgentInteraction(
        fromAgent: String,
        toAgent: String,
        eventType: String,
        eventId: String?,
        correlationId: String?,
    ) {
    }
}

/**
 * Zero-allocation, no-op [Tracer]. The broker's default.
 */
public object NullTracer : Tracer
