package com.mojentic.llm

import com.mojentic.errors.LlmGatewayException
import com.mojentic.errors.MaxToolIterationsExceededException
import com.mojentic.internal.JsonSchemaGenerator
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.SerialToolRunner
import com.mojentic.llm.tools.ToolOutcome
import com.mojentic.llm.tools.ToolRunner
import com.mojentic.tracer.NullTracer
import com.mojentic.tracer.Tracer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.takeWhile
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.serializer as topLevelSerializer

@OptIn(ExperimentalUuidApi::class)
private fun newCorrelationId(): String = Uuid.random().toString()

private val logger = KotlinLogging.logger {}

/**
 * Coordinator above an [LlmGateway] that handles tool execution and provides
 * a Kotlin-idiomatic chat surface.
 *
 * The broker is the primary integration point for application code. It:
 * - Forwards single-shot, streaming, and structured-output requests to the gateway.
 * - Dispatches tool calls through a [ToolRunner] (serial by default) and
 *   recursively re-prompts the LLM with the tool results.
 * - Honours [CompletionConfig.maxToolIterations] as a hard recursion ceiling.
 * - Retains completed native assistant reasoning with its tool calls only when
 *   [CompletionConfig.preserveReasoning] is selected. Failed/cancelled streams
 *   never produce a completed native transcript.
 * - Records observability hooks to a [Tracer] (no-op by default).
 *
 * The broker is stateless from the caller's point of view: each call carries
 * its own message list. Use `ChatSession` (Phase 2) when you want managed
 * conversation state.
 *
 * Recovery configuration is validated when [com.mojentic.llm.recovery.RecoveryPolicy]
 * is constructed. Invalid values throw [com.mojentic.errors.InvalidRecoveryPolicyException]
 * before any completion, callback or tool execution; these APIs accept only validated policies.
 */
public class LlmBroker(
    private val gateway: LlmGateway,
    private val tracer: Tracer = NullTracer,
    private val toolRunner: ToolRunner = SerialToolRunner(),
    @PublishedApi internal val json: Json = DEFAULT_JSON,
) {
    public suspend fun complete(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool> = emptyList(),
        config: CompletionConfig = CompletionConfig(),
        correlationId: String? = null,
    ): LlmGatewayResponse = completeConversation(model, messages, tools, config, correlationId) {}

    internal suspend fun completeConversation(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>,
        config: CompletionConfig,
        correlationId: String?,
        onCompleted: suspend (List<LlmMessage>) -> Unit,
    ): LlmGatewayResponse {
        config.validateReasoning(messages.any { it.reasoningContent != null })
        ensureBudget(config, model)
        val cid = correlationId ?: newCorrelationId()
        val response = generateResponse(model, messages, tools, config, cid)

        if (response.toolCalls.isEmpty()) {
            onCompleted(messages + response.asHistory(config))
            return response
        }
        val outcomes = dispatchTools(response.toolCalls, tools, cid)
        if (outcomes.isEmpty()) return response
        val nextMessages = messages + toolMessagesFor(outcomes, response, config)
        return completeConversation(
            model = model,
            messages = nextMessages,
            tools = tools,
            config = config.copy(maxToolIterations = config.maxToolIterations?.minus(1)),
            correlationId = cid,
            onCompleted = onCompleted,
        )
    }

    /** Return one native response without dispatching tools or changing context. */
    public suspend fun generateResponse(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool> = emptyList(),
        config: CompletionConfig = CompletionConfig(),
        correlationId: String? = null,
    ): LlmGatewayResponse {
        config.validateReasoning(messages.any { it.reasoningContent != null })
        val cid = correlationId ?: newCorrelationId()
        tracer.recordLlmCall(model, messages, config.temperature, toolNames(tools), cid)
        val mark = TimeSource.Monotonic.markNow()
        val response = gateway.complete(model, messages, tools.takeIf { it.isNotEmpty() }, config)
        recordResponse(model, response, response.content, mark.elapsedNow(), cid)

        return response
    }

    public suspend inline fun <reified T> completeJson(
        model: String,
        messages: List<LlmMessage>,
        config: CompletionConfig = CompletionConfig(),
        correlationId: String? = null,
    ): T {
        val schema = JsonSchemaGenerator.schemaFor<T>()
        val element = completeJsonElement(model, messages, schema, config, correlationId)
        val deserializer: DeserializationStrategy<T> = topLevelSerializer<T>()
        return json.decodeFromJsonElement(deserializer, element)
    }

    @PublishedApi
    internal suspend fun completeJsonElement(
        model: String,
        messages: List<LlmMessage>,
        schema: JsonObject,
        config: CompletionConfig,
        correlationId: String?,
    ): JsonObject {
        config.validateReasoning(messages.any { it.reasoningContent != null })
        val cid = correlationId ?: newCorrelationId()
        tracer.recordLlmCall(model, messages, config.temperature, tools = null, correlationId = cid)
        val mark = TimeSource.Monotonic.markNow()
        val response = gateway.completeJsonResponse(model, messages, schema, config)
        val result = response.structuredJson as? JsonObject
            ?: throw LlmGatewayException("Structured-output response carried no JSON object")
        recordResponse(model, response, result.toString(), mark.elapsedNow(), cid)
        return result
    }

    private suspend fun recordResponse(
        model: String,
        response: LlmGatewayResponse,
        content: String?,
        callDuration: Duration,
        correlationId: String,
    ) {
        tracer.recordLlmResponse(
            model = model,
            content = content,
            toolCalls = response.toolCalls.takeIf { it.isNotEmpty() },
            callDuration = callDuration,
            correlationId = correlationId,
            usage = response.usage,
            providerModel = response.providerModel,
            finishReason = response.finishReason,
            metadata = response.metadata,
        )
    }

    public fun stream(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool> = emptyList(),
        config: CompletionConfig = CompletionConfig(),
        correlationId: String? = null,
    ): Flow<StreamEvent> = streamConversation(model, messages, tools, config, correlationId) {}

    internal fun streamConversation(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>,
        config: CompletionConfig,
        correlationId: String?,
        onCompleted: suspend (List<LlmMessage>) -> Unit,
    ): Flow<StreamEvent> = flow {
        config.validateReasoning(messages.any { it.reasoningContent != null })
        ensureBudget(config, model)
        val cid = correlationId ?: newCorrelationId()
        tracer.recordLlmCall(model, messages, config.temperature, toolNames(tools), cid)
        val accumulatedToolCalls = mutableListOf<LlmToolCall>()
        val contentBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        val mark = TimeSource.Monotonic.markNow()
        gateway.stream(model, messages, tools.takeIf { it.isNotEmpty() }, config).collect { event ->
            when (event) {
                is GatewayStreamEvent.Content -> {
                    contentBuilder.append(event.text)
                    emit(StreamEvent.TextChunk(event.text))
                }

                is GatewayStreamEvent.Thinking -> {
                    if (config.preserveReasoning) reasoningBuilder.append(event.text)
                    emit(StreamEvent.ThinkingChunk(event.text))
                }

                is GatewayStreamEvent.ToolCalls -> accumulatedToolCalls += event.calls

                is GatewayStreamEvent.Raw -> { /* no-op by default */ }
            }
        }
        val response = LlmGatewayResponse(
            content = contentBuilder.toString().ifEmpty { null },
            thinking = reasoningBuilder.toString().ifEmpty { null },
            toolCalls = accumulatedToolCalls,
        )
        recordResponse(model, response, response.content, mark.elapsedNow(), cid)
        if (accumulatedToolCalls.isEmpty()) {
            onCompleted(messages + response.asHistory(config))
            return@flow
        }
        val outcomes = dispatchTools(accumulatedToolCalls, tools, cid)
        if (outcomes.isEmpty()) return@flow
        for (outcome in outcomes) {
            emit(StreamEvent.ToolCall(outcome.call))
            emit(
                StreamEvent.ToolResult(
                    call = outcome.call,
                    result = outcome.result ?: errorJson(outcome.error),
                    isError = !outcome.isOk,
                ),
            )
        }
        val nextMessages = messages + toolMessagesFor(outcomes, response, config)
        emitAll(
            streamConversation(
                model = model,
                messages = nextMessages,
                tools = tools,
                config = config.copy(maxToolIterations = config.maxToolIterations?.minus(1)),
                correlationId = cid,
                onCompleted = onCompleted,
            ),
        )
    }

    /**
     * Stream one turn as [CompletionStreamEvent]s with terminal completion evidence.
     *
     * Emits [CompletionStreamEvent.Content] events in order, then exactly one
     * terminal event: [CompletionStreamEvent.Completed] when the provider
     * reported a normal stop and ended the stream, or [CompletionStreamEvent.Error]
     * otherwise. Truncated or unfinished output is an error, never a result;
     * content received before an error is evidence only.
     *
     * The turn sends one request with no tools, forces
     * [CompletionConfig.maxToolIterations] to zero. Opt-in completion gateway recovery
     * can resend only the same completion before semantic progress, after admission. Stopping
     * collection early cancels the request. A gateway that does not implement
     * [StreamEventsGateway] yields a single
     * [StreamErrorReason.StreamEventsUnsupported] error, with no request and no trace.
     *
     * The call is traced when the request starts. The response, with the
     * content received and the terminal evidence, is traced when the terminal
     * event arrives. A consumer that stops early leaves a traced call and no
     * traced response.
     *
     * @param model Provider-side model identifier.
     * @param messages Conversation history.
     * @param config Completion knobs, including [CompletionConfig.responseFormat].
     * @param correlationId Correlation ID for tracer events; generated when null.
     */
    public fun generateStreamEvents(
        model: String,
        messages: List<LlmMessage>,
        config: CompletionConfig = CompletionConfig(),
        correlationId: String? = null,
    ): Flow<CompletionStreamEvent> {
        val source = gateway as? StreamEventsGateway
            ?: return flowOf(CompletionStreamEvent.Error(StreamErrorReason.StreamEventsUnsupported))
        return flow {
            val cid = correlationId ?: newCorrelationId()
            tracer.recordLlmCall(model, messages, config.temperature, tools = null, correlationId = cid)
            val mark = TimeSource.Monotonic.markNow()
            val content = StringBuilder()
            var terminal: CompletionStreamEvent? = null
            val eventFlow = source.streamEvents(model, messages, config.copy(maxToolIterations = 0))
                .catch { failure ->
                    if (config.recovery != null && failure is CancellationException) throw failure
                    emit(CompletionStreamEvent.Error(StreamErrorReason.RequestFailed(failure)))
                }
            val outcome = if (config.recovery != null) {
                collectRecoveryStreamEvents(eventFlow, content) { emit(it) }
            } else {
                eventFlow
                    .takeWhile { event -> (event is CompletionStreamEvent.Content).also { if (!it) terminal = event } }
                    .collect { event ->
                        if (event is CompletionStreamEvent.Content) {
                            content.append(event.text)
                            emit(event)
                        }
                    }
                terminal ?: CompletionStreamEvent.Error(StreamErrorReason.IncompleteStream(evidence = null))
            }
            val evidence = outcome.evidence
            tracer.recordLlmResponse(
                model = model,
                content = content.toString(),
                toolCalls = null,
                callDuration = mark.elapsedNow(),
                correlationId = cid,
                usage = evidence?.usage,
                providerModel = evidence?.providerModel,
                finishReason = evidence?.finishReason,
                metadata = evidence?.metadata,
            )
            emit(outcome)
        }
    }

    private suspend fun dispatchTools(calls: List<LlmToolCall>, tools: List<LlmTool>, correlationId: String): List<ToolOutcome> {
        val outcomes = toolRunner.runBatch(calls, tools, correlationId)
        outcomes.forEach { outcome ->
            tracer.recordToolCall(
                toolName = outcome.call.name,
                arguments = outcome.call.arguments.toString(),
                result = outcome.result ?: errorJson(outcome.error),
                callDuration = outcome.duration,
                isError = !outcome.isOk,
                correlationId = correlationId,
            )
        }
        return outcomes
    }

    private fun toolMessagesFor(
        outcomes: List<ToolOutcome>,
        response: LlmGatewayResponse,
        config: CompletionConfig,
    ): List<LlmMessage> {
        if (!config.preserveReasoning) {
            return outcomes.flatMap { outcome ->
                listOf(LlmMessage.assistant(toolCalls = listOf(outcome.call)), toolMessage(outcome))
            }
        }
        return listOf(
            LlmMessage(
                role = MessageRole.Assistant,
                content = response.content,
                toolCalls = response.toolCalls,
                reasoningContent = response.thinking,
            ),
        ) + outcomes.map { toolMessage(it) }
    }

    private fun toolMessage(outcome: ToolOutcome): LlmMessage = LlmMessage.tool(
        content = outcome.result ?: errorJson(outcome.error),
        toolCall = outcome.call,
    )

    private fun ensureBudget(config: CompletionConfig, model: String) {
        if (config.maxToolIterations != null && config.maxToolIterations <= 0) {
            throw MaxToolIterationsExceededException(
                "Tool-call iterations exceeded the maximum budget for model '$model'. " +
                    "Increase config.maxToolIterations to allow deeper recursion.",
            )
        }
    }

    private fun toolNames(tools: List<LlmTool>): List<String>? = tools.takeIf { it.isNotEmpty() }?.map { it.name }

    private fun errorJson(error: Throwable?): String {
        val message = error?.message ?: error?.toString() ?: "unknown tool error"
        return Json.encodeToString(MAP_SERIALIZER, mapOf("error" to message))
    }

    public companion object {
        public val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
        }

        private val MAP_SERIALIZER = MapSerializer(String.serializer(), String.serializer())
    }
}

private val CompletionStreamEvent.evidence: CompletionEvidence?
    get() = when (this) {
        is CompletionStreamEvent.Completed -> evidence

        is CompletionStreamEvent.Error -> when (val cause = reason) {
            is StreamErrorReason.IncompleteCompletion -> cause.evidence
            is StreamErrorReason.IncompleteStream -> cause.evidence
            is StreamErrorReason.RequestFailed -> (cause.cause as? RecoveryException)?.failures?.lastOrNull()?.inspectCompletionEvidence()
            else -> null
        }

        is CompletionStreamEvent.Content -> null
    }

private fun LlmGatewayResponse.asHistory(config: CompletionConfig): LlmMessage = LlmMessage(
    role = MessageRole.Assistant,
    content = content,
    toolCalls = toolCalls.takeIf { it.isNotEmpty() },
    reasoningContent = thinking.takeIf { config.preserveReasoning },
)
