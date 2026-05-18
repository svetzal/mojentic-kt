package com.mojentic.llm

import com.mojentic.errors.MaxToolIterationsExceededException
import com.mojentic.internal.JsonSchemaGenerator
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.SerialToolRunner
import com.mojentic.llm.tools.ToolOutcome
import com.mojentic.llm.tools.ToolRunner
import com.mojentic.tracer.NullTracer
import com.mojentic.tracer.Tracer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
 * - Records observability hooks to a [Tracer] (no-op by default).
 *
 * The broker is stateless from the caller's point of view: each call carries
 * its own message list. Use `ChatSession` (Phase 2) when you want managed
 * conversation state.
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
    ): LlmGatewayResponse {
        ensureBudget(config, model)
        val cid = correlationId ?: newCorrelationId()
        tracer.recordLlmCall(model, messages, config.temperature, toolNames(tools), cid)
        val mark = TimeSource.Monotonic.markNow()
        val response = gateway.complete(model, messages, tools.takeIf { it.isNotEmpty() }, config)
        tracer.recordLlmResponse(
            model = model,
            content = response.content,
            toolCalls = response.toolCalls.takeIf { it.isNotEmpty() },
            callDuration = mark.elapsedNow(),
            correlationId = cid,
        )

        if (response.toolCalls.isEmpty() || tools.isEmpty()) return response
        val outcomes = dispatchTools(response.toolCalls, tools, cid)
        if (outcomes.isEmpty()) return response
        val nextMessages = messages + toolMessagesFor(outcomes)
        return complete(
            model = model,
            messages = nextMessages,
            tools = tools,
            config = config.copy(maxToolIterations = config.maxToolIterations - 1),
            correlationId = cid,
        )
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
        val cid = correlationId ?: newCorrelationId()
        tracer.recordLlmCall(model, messages, config.temperature, tools = null, correlationId = cid)
        val mark = TimeSource.Monotonic.markNow()
        val result = gateway.completeJson(model, messages, schema, config)
        tracer.recordLlmResponse(
            model = model,
            content = result.toString(),
            toolCalls = null,
            callDuration = mark.elapsedNow(),
            correlationId = cid,
        )
        return result
    }

    public fun stream(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool> = emptyList(),
        config: CompletionConfig = CompletionConfig(),
        correlationId: String? = null,
    ): Flow<StreamEvent> = flow {
        ensureBudget(config, model)
        val cid = correlationId ?: newCorrelationId()
        tracer.recordLlmCall(model, messages, config.temperature, toolNames(tools), cid)
        val accumulatedToolCalls = mutableListOf<LlmToolCall>()
        val contentBuilder = StringBuilder()
        val mark = TimeSource.Monotonic.markNow()
        gateway.stream(model, messages, tools.takeIf { it.isNotEmpty() }, config).collect { event ->
            when (event) {
                is GatewayStreamEvent.Content -> {
                    contentBuilder.append(event.text)
                    emit(StreamEvent.TextChunk(event.text))
                }
                is GatewayStreamEvent.Thinking -> emit(StreamEvent.ThinkingChunk(event.text))
                is GatewayStreamEvent.ToolCalls -> accumulatedToolCalls += event.calls
                is GatewayStreamEvent.Raw -> { /* no-op by default */ }
            }
        }
        tracer.recordLlmResponse(
            model = model,
            content = contentBuilder.toString().ifEmpty { null },
            toolCalls = accumulatedToolCalls.takeIf { it.isNotEmpty() },
            callDuration = mark.elapsedNow(),
            correlationId = cid,
        )
        if (accumulatedToolCalls.isEmpty() || tools.isEmpty()) return@flow
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
        val nextMessages = messages + toolMessagesFor(outcomes)
        emitAll(
            stream(
                model = model,
                messages = nextMessages,
                tools = tools,
                config = config.copy(maxToolIterations = config.maxToolIterations - 1),
                correlationId = cid,
            ),
        )
    }

    private suspend fun dispatchTools(calls: List<LlmToolCall>, tools: List<LlmTool>, correlationId: String): List<ToolOutcome> {
        val (known, unknown) = calls.partition { call -> tools.any { it.matches(call.name) } }
        unknown.forEach { logger.warn { "Tool not found for call ${it.name}" } }
        if (known.isEmpty()) return emptyList()
        val outcomes = toolRunner.runBatch(known, tools)
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

    private fun toolMessagesFor(outcomes: List<ToolOutcome>): List<LlmMessage> = outcomes.flatMap { outcome ->
        listOf(
            LlmMessage.assistant(toolCalls = listOf(outcome.call)),
            LlmMessage.tool(
                content = outcome.result ?: errorJson(outcome.error),
                toolCall = outcome.call,
            ),
        )
    }

    private fun ensureBudget(config: CompletionConfig, model: String) {
        if (config.maxToolIterations <= 0) {
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
