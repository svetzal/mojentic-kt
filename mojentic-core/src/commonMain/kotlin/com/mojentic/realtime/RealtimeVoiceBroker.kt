package com.mojentic.realtime

import com.mojentic.llm.tools.ParallelToolRunner
import com.mojentic.llm.tools.ToolRunner
import com.mojentic.tracer.NullTracer
import com.mojentic.tracer.Tracer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Coordinator above a [RealtimeGateway] — opens sessions, threads in the
 * tool runner + tracer, and produces stateful [RealtimeSession] handles.
 *
 * `RealtimeVoiceBroker` is **not** a subclass of `LlmBroker`. Realtime has a
 * different lifecycle (long-lived duplex session vs one-shot generate),
 * different I/O (audio streams vs strings), and different concurrency model
 * (parallel tool calls per turn vs serial). Composition over inheritance —
 * the two coordinators are siblings.
 *
 * The default tool runner is [ParallelToolRunner] because realtime turns can
 * legitimately emit several tool calls concurrently and serialising them
 * burns the latency budget the voice modality is built around.
 */
public class RealtimeVoiceBroker(
    private val gateway: RealtimeGateway,
    private val toolRunner: ToolRunner = ParallelToolRunner(),
    private val tracer: Tracer = NullTracer,
) {

    /**
     * Open a new realtime session.
     *
     * The returned [RealtimeSession] owns the underlying transport; the
     * broker itself is reusable across many concurrent sessions.
     *
     * @param scope Coroutine scope used to launch the session's reader and
     *   tool-dispatch coroutines. A reasonable default of `SupervisorJob() +
     *   Dispatchers.Default` is used when omitted; supply an explicit scope
     *   to tie the session lifetime to your application's structured
     *   concurrency.
     */
    public suspend fun connect(
        model: String,
        config: RealtimeVoiceConfig,
        correlationId: String? = null,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ): RealtimeSession {
        val gatewaySession = gateway.open(model, config, correlationId)
        return RealtimeSession.create(
            gatewaySession = gatewaySession,
            tools = config.tools,
            toolRunner = toolRunner,
            tracer = tracer,
            correlationId = correlationId,
            scope = scope,
        )
    }
}
