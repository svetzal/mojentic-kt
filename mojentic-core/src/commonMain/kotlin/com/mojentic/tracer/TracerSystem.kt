package com.mojentic.tracer

import com.mojentic.llm.LlmMessage
import com.mojentic.llm.LlmToolCall
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Central [Tracer] implementation backed by an [EventStore].
 *
 * Every recorder method packages its arguments into a [TracerEvent] and
 * stores it in the underlying buffer; live consumers can subscribe via
 * `eventStore.events`.
 *
 * Disable the system globally with [disable] / [enable] to skip recording
 * without changing wiring.
 *
 * Mirrors the Python reference `TracerSystem` (`tracer/tracer_system.py`).
 */
public class TracerSystem(
    public val eventStore: EventStore = EventStore(),
    enabled: Boolean = true,
) : Tracer {

    private var _enabled: Boolean = enabled
    public val enabled: Boolean get() = _enabled

    override suspend fun recordLlmCall(
        model: String,
        messages: List<LlmMessage>,
        temperature: Double,
        tools: List<String>?,
        correlationId: String?,
    ) {
        if (!_enabled) return
        eventStore.store(
            LlmCallEvent(
                timestamp = Clock.System.now(),
                correlationId = correlationId.orEmpty(),
                model = model,
                messages = messages,
                temperature = temperature,
                tools = tools,
            ),
        )
    }

    override suspend fun recordLlmResponse(
        model: String,
        content: String?,
        toolCalls: List<LlmToolCall>?,
        callDuration: Duration,
        correlationId: String?,
    ) {
        if (!_enabled) return
        eventStore.store(
            LlmResponseEvent(
                timestamp = Clock.System.now(),
                correlationId = correlationId.orEmpty(),
                model = model,
                content = content,
                toolCalls = toolCalls,
                callDuration = callDuration,
            ),
        )
    }

    override suspend fun recordToolCall(
        toolName: String,
        arguments: String,
        result: String,
        callDuration: Duration,
        isError: Boolean,
        correlationId: String?,
        caller: String?,
    ) {
        if (!_enabled) return
        eventStore.store(
            ToolCallEvent(
                timestamp = Clock.System.now(),
                correlationId = correlationId.orEmpty(),
                toolName = toolName,
                arguments = arguments,
                result = result,
                callDuration = callDuration,
                isError = isError,
                caller = caller,
            ),
        )
    }

    override suspend fun recordToolBatch(
        batchId: String,
        toolNames: List<String>,
        successCount: Int,
        failureCount: Int,
        callDuration: Duration,
        correlationId: String?,
        caller: String?,
    ) {
        if (!_enabled) return
        eventStore.store(
            ToolBatchEvent(
                timestamp = Clock.System.now(),
                correlationId = correlationId.orEmpty(),
                batchId = batchId,
                toolNames = toolNames,
                successCount = successCount,
                failureCount = failureCount,
                callDuration = callDuration,
                caller = caller,
            ),
        )
    }

    override suspend fun recordAgentInteraction(
        fromAgent: String,
        toAgent: String,
        eventType: String,
        eventId: String?,
        correlationId: String?,
    ) {
        if (!_enabled) return
        eventStore.store(
            AgentInteractionEvent(
                timestamp = Clock.System.now(),
                correlationId = correlationId.orEmpty(),
                fromAgent = fromAgent,
                toAgent = toAgent,
                eventType = eventType,
                eventId = eventId,
            ),
        )
    }

    /** Enable event recording (default). */
    public fun enable() {
        _enabled = true
    }

    /** Disable event recording without changing wiring. */
    public fun disable() {
        _enabled = false
    }

    /** Drop every event from the underlying [EventStore]. */
    public suspend fun clear() {
        eventStore.clear()
    }
}
