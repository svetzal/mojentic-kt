package com.mojentic.tracer

import com.mojentic.llm.LlmMessage
import com.mojentic.llm.LlmToolCall
import kotlin.time.Duration

/**
 * Phase 1 tracer interface.
 *
 * The full Tracer / EventStore system lands in Phase 3 (see KOTLIN.md). For now
 * this interface defines the broker integration points so the broker can call
 * tracer hooks unconditionally without branching on null. Phase 1 callers use
 * [NullTracer] (the default) which is a true no-op.
 *
 * Correlation IDs are opaque strings so the public surface does not depend on
 * still-experimental `kotlin.uuid.Uuid`. The broker generates UUID-shaped strings
 * internally when callers do not supply their own.
 */
public interface Tracer {
    public fun recordLlmCall(
        model: String,
        messages: List<LlmMessage>,
        temperature: Double,
        tools: List<String>?,
        correlationId: String?,
    ) {
    }

    public fun recordLlmResponse(
        model: String,
        content: String?,
        toolCalls: List<LlmToolCall>?,
        callDuration: Duration,
        correlationId: String?,
    ) {
    }

    public fun recordToolCall(
        toolName: String,
        arguments: String,
        result: String,
        callDuration: Duration,
        isError: Boolean,
        correlationId: String?,
    ) {
    }
}

/**
 * Zero-allocation, no-op [Tracer]. The broker's default.
 */
public object NullTracer : Tracer
