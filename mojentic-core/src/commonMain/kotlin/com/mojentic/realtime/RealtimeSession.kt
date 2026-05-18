package com.mojentic.realtime

import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolRunner
import com.mojentic.realtime.internal.RealtimeEventNormalizer
import com.mojentic.tracer.Tracer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

private val logger = KotlinLogging.logger("com.mojentic.realtime.RealtimeSession")

/**
 * Stateful handle to a single open realtime conversation.
 *
 * Owns the demultiplexing of raw provider events into the vendor-neutral
 * [RealtimeEvent] union, and the dispatch of model-requested tool calls
 * through the broker's [ToolRunner]. Audio I/O and barge-in arrive in slice
 * C; this slice covers text input + parallel tool calls.
 *
 * Instances are produced by [RealtimeVoiceBroker.connect]; the public
 * constructor is internal because lifecycle setup (event reader coroutine,
 * tool dispatcher) lives in [create].
 */
internal data class RealtimeSessionWiring(
    val emitter: MutableSharedFlow<RealtimeEvent>,
    val rawEmitter: MutableSharedFlow<JsonObject>,
    val normalizer: RealtimeEventNormalizer,
)

public class RealtimeSession internal constructor(
    private val gatewaySession: RealtimeGatewaySession,
    private val tools: List<LlmTool>,
    private val toolRunner: ToolRunner,
    private val tracer: Tracer,
    private val correlationId: String?,
    private val scope: CoroutineScope,
    private val wiring: RealtimeSessionWiring,
) {

    private val emitter: MutableSharedFlow<RealtimeEvent> get() = wiring.emitter
    private val rawEmitter: MutableSharedFlow<JsonObject> get() = wiring.rawEmitter
    private val normalizer: RealtimeEventNormalizer get() = wiring.normalizer

    /** Turn id currently being produced by the assistant, or null when idle. */
    private var inFlightTurnId: String? = null

    /** Active tool-dispatch job for the current turn; cancelling it aborts in-flight tools. */
    private var toolDispatchJob: Job? = null

    private val readerJob: Job = scope.launch { readerLoop() }

    /** Cold-replay flow of vendor-neutral [RealtimeEvent]s. */
    public val events: Flow<RealtimeEvent> = wiring.emitter.asSharedFlow()

    /** Power-user / debugging surface: raw provider events, re-published by the broker. */
    public val rawEvents: Flow<JsonObject> = wiring.rawEmitter.asSharedFlow()

    /** Send a text user message into the conversation and ask for a response. */
    public suspend fun sendText(text: String) {
        gatewaySession.send(ClientRealtimeEvent.UserText(text))
        gatewaySession.send(ClientRealtimeEvent.ResponseCreate)
    }

    /**
     * Stream PCM16 audio frames into the session's input buffer.
     *
     * Returns a [Job] tied to the broker's scope so the caller can cancel
     * the audio pump (e.g. when the user stops talking). Each frame becomes
     * one `input_audio_buffer.append` event on the wire.
     */
    public fun sendAudio(audio: Flow<AudioFrame>): Job = scope.launch {
        audio.collect { frame ->
            gatewaySession.send(ClientRealtimeEvent.InputAudioBufferAppend(frame))
        }
    }

    /**
     * Manually commit the input audio buffer and ask for a response.
     *
     * Only meaningful when the session was opened with [VadConfig.Manual].
     * Under server VAD the provider commits automatically when it detects
     * a pause, and calling this is harmless but redundant.
     */
    public suspend fun commit() {
        gatewaySession.send(ClientRealtimeEvent.InputAudioBufferCommit)
        gatewaySession.send(ClientRealtimeEvent.ResponseCreate)
    }

    /** Clear pending audio in the input buffer without committing. */
    public suspend fun clearAudio() {
        gatewaySession.send(ClientRealtimeEvent.InputAudioBufferClear)
    }

    /**
     * Cancel any in-flight response and abort tool execution.
     *
     * Used for manual barge-in. In slice C the same path fires automatically
     * when server VAD reports the user started speaking mid-response.
     */
    public suspend fun interrupt() {
        gatewaySession.send(ClientRealtimeEvent.ResponseCancel)
        emitter.emit(RealtimeEvent.Interrupted(turnId = null, reason = RealtimeEvent.InterruptionReason.Manual))
    }

    /** Close the session and tear down the gateway transport. */
    public suspend fun close() {
        try {
            gatewaySession.close()
        } finally {
            emitter.emit(RealtimeEvent.SessionClosed(RealtimeEvent.CloseReason.Client))
            readerJob.cancel()
        }
    }

    /** Suspend until the next [RealtimeEvent.AssistantTurnCompleted] arrives. */
    public suspend fun awaitTurnCompleted(): RealtimeEvent.AssistantTurnCompleted =
        events.first { it is RealtimeEvent.AssistantTurnCompleted } as RealtimeEvent.AssistantTurnCompleted

    private suspend fun readerLoop() {
        gatewaySession.rawEvents.collect { raw ->
            rawEmitter.emit(raw)
            val translated = try {
                normalizer.translate(raw)
            } catch (failure: Throwable) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                logger.warn(failure) { "Failed to normalise realtime event" }
                return@collect
            }
            for (event in translated) {
                emitter.emit(event)
                trackTurnState(event)
                if (event is RealtimeEvent.UserSpeechStarted && inFlightTurnId != null) {
                    triggerBargeIn()
                }
            }
            if (translated.any { it is RealtimeEvent.AssistantTurnCompleted }) {
                launchToolDispatch()
            }
        }
    }

    private fun trackTurnState(event: RealtimeEvent) {
        when (event) {
            is RealtimeEvent.AssistantTurnStarted -> {
                inFlightTurnId = event.turnId
            }
            is RealtimeEvent.AssistantTurnCompleted, is RealtimeEvent.Interrupted -> {
                inFlightTurnId = null
            }
            else -> Unit
        }
    }

    private suspend fun triggerBargeIn() {
        val turnId = inFlightTurnId
        toolDispatchJob?.cancel()
        toolDispatchJob = null
        gatewaySession.send(ClientRealtimeEvent.ResponseCancel)
        emitter.emit(RealtimeEvent.Interrupted(turnId = turnId, reason = RealtimeEvent.InterruptionReason.BargeIn))
        inFlightTurnId = null
    }

    private fun launchToolDispatch() {
        val toolCalls = normalizer.consumeAllReady()
        if (toolCalls.isEmpty()) return
        toolDispatchJob = scope.launch { dispatchTools(toolCalls) }
    }

    private suspend fun dispatchTools(toolCalls: List<com.mojentic.llm.LlmToolCall>) {
        val outcomes = toolRunner.runBatch(toolCalls, tools, correlationId)
        val submittedIds = mutableListOf<String>()
        for (outcome in outcomes) {
            val callId = outcome.call.id ?: continue
            val toolName = outcome.call.name
            if (outcome.isOk) {
                val resultText = outcome.result.orEmpty()
                gatewaySession.send(ClientRealtimeEvent.FunctionCallOutput(callId, resultText))
                emitter.emit(RealtimeEvent.ToolCallCompleted(callId, toolName, resultText))
                submittedIds += callId
            } else {
                val error = outcome.error ?: IllegalStateException("Tool failed without an error attached")
                val payload = """{"error":"${error.message ?: "tool execution failed"}"}"""
                gatewaySession.send(ClientRealtimeEvent.FunctionCallOutput(callId, payload))
                emitter.emit(RealtimeEvent.ToolCallFailed(callId, toolName, error))
                submittedIds += callId
            }
        }
        if (submittedIds.isNotEmpty()) {
            emitter.emit(RealtimeEvent.ToolBatchSubmitted(turnId = "", callIds = submittedIds))
            gatewaySession.send(ClientRealtimeEvent.ResponseCreate)
        }
    }

    internal companion object {
        internal fun create(
            gatewaySession: RealtimeGatewaySession,
            tools: List<LlmTool>,
            toolRunner: ToolRunner,
            tracer: Tracer,
            correlationId: String?,
            scope: CoroutineScope,
        ): RealtimeSession {
            val wiring = RealtimeSessionWiring(
                emitter = MutableSharedFlow(replay = EMITTER_REPLAY, extraBufferCapacity = EMITTER_BUFFER),
                rawEmitter = MutableSharedFlow(replay = EMITTER_REPLAY, extraBufferCapacity = EMITTER_BUFFER),
                normalizer = RealtimeEventNormalizer(),
            )
            return RealtimeSession(
                gatewaySession = gatewaySession,
                tools = tools,
                toolRunner = toolRunner,
                tracer = tracer,
                correlationId = correlationId,
                scope = scope,
                wiring = wiring,
            )
        }

        private const val EMITTER_BUFFER: Int = 128
        private const val EMITTER_REPLAY: Int = 256
    }
}
