package com.mojentic.anthropic

import com.mojentic.anthropic.internal.AnthropicErrorResponse
import com.mojentic.anthropic.internal.AnthropicMessagesRequest
import com.mojentic.anthropic.internal.AnthropicMessagesResponse
import com.mojentic.anthropic.internal.AnthropicModelsListResponse
import com.mojentic.anthropic.internal.AnthropicResponseContent
import com.mojentic.anthropic.internal.AnthropicTool
import com.mojentic.anthropic.internal.AnthropicToolChoice
import com.mojentic.anthropic.internal.toAnthropicMessages
import com.mojentic.anthropic.internal.toAnthropicTools
import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmGateway
import com.mojentic.llm.LlmGatewayResponse
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.LlmToolCall
import com.mojentic.llm.tools.LlmTool
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readLine
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val logger = KotlinLogging.logger {}

/**
 * Default Anthropic HTTP host.
 */
public const val DEFAULT_ANTHROPIC_HOST: String = "https://api.anthropic.com"

/**
 * Default API version (sent in the `anthropic-version` header).
 */
public const val DEFAULT_ANTHROPIC_VERSION: String = "2023-06-01"

/**
 * Default schema-name used when `completeJson` forces the model to emit a JSON
 * object via a synthetic tool. Anthropic does not yet expose a first-class
 * `response_format: { type: "json_schema" }` knob, so we wrap the schema in a
 * single forced tool call.
 */
internal const val STRUCTURED_RESPONSE_TOOL_NAME: String = "respond_in_json"

/**
 * Ktor-Client backed gateway to the Anthropic Messages API.
 *
 * Mirrors the Python reference (`mojentic.llm.gateways.anthropic.AnthropicGateway`)
 * with Kotlin idioms (suspend, structured concurrency, Flow streaming) and the
 * Kotlin-specific shape of the [LlmGateway] interface.
 *
 * @param apiKey API key sent in the `x-api-key` header. Required.
 * @param host Base URL of the Anthropic HTTP API. Override for Anthropic-compatible
 *             endpoints (e.g. AWS Bedrock proxies that speak the Anthropic protocol).
 * @param anthropicVersion Value of the mandatory `anthropic-version` header.
 * @param engine Optional Ktor engine override. Tests inject a `MockEngine` here.
 * @param json Json codec used for request / response (de)serialisation.
 */
public class AnthropicGateway(
    private val apiKey: String,
    private val host: String = DEFAULT_ANTHROPIC_HOST,
    private val anthropicVersion: String = DEFAULT_ANTHROPIC_VERSION,
    engine: HttpClientEngine? = null,
    private val json: Json = DEFAULT_JSON,
) : LlmGateway {
    private val httpClient: HttpClient = buildHttpClient(engine)

    /**
     * Closes the underlying Ktor client.
     */
    public fun close() {
        httpClient.close()
    }

    override suspend fun complete(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
    ): LlmGatewayResponse {
        val request = buildMessagesRequest(
            model = model,
            messages = messages,
            tools = tools?.toAnthropicTools(),
            toolChoice = null,
            config = config,
            stream = false,
        )
        val response = postMessages(request)
        return response.toGatewayResponse()
    }

    override suspend fun completeJson(
        model: String,
        messages: List<LlmMessage>,
        schema: JsonObject,
        config: CompletionConfig,
    ): JsonObject {
        val syntheticTool = AnthropicTool(
            name = STRUCTURED_RESPONSE_TOOL_NAME,
            description = "Return the structured response object the user asked for.",
            inputSchema = schema,
        )
        val request = buildMessagesRequest(
            model = model,
            messages = messages,
            tools = listOf(syntheticTool),
            toolChoice = AnthropicToolChoice(type = "tool", name = STRUCTURED_RESPONSE_TOOL_NAME),
            config = config,
            stream = false,
        )
        val response = postMessages(request)
        val toolUse = response.content
            .filterIsInstance<AnthropicResponseContent.ToolUse>()
            .firstOrNull { it.name == STRUCTURED_RESPONSE_TOOL_NAME }
            ?: throw LlmGatewayException(
                "Anthropic returned no `$STRUCTURED_RESPONSE_TOOL_NAME` tool_use for completeJson",
            )
        return toolUse.input
    }

    override fun stream(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
    ): Flow<GatewayStreamEvent> = kotlinx.coroutines.flow.flow {
        val request = buildMessagesRequest(
            model = model,
            messages = messages,
            tools = tools?.toAnthropicTools(),
            toolChoice = null,
            config = config,
            stream = true,
        )
        val httpResponse: HttpResponse = httpClient.post("$host/v1/messages") {
            applyMessagesHeaders()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AnthropicMessagesRequest.serializer(), request))
        }
        ensureSuccess(httpResponse)
        val accumulator = AnthropicStreamAccumulator(json)
        val channel = httpResponse.bodyAsChannel()
        var stopped = false
        while (!stopped) {
            val line = channel.readLine() ?: break
            stopped = handleSseLine(line, accumulator) { event -> emit(event) }
        }
        val finalised = accumulator.toolCalls()
        if (finalised.isNotEmpty()) emit(GatewayStreamEvent.ToolCalls(finalised))
    }

    private suspend fun handleSseLine(
        line: String,
        accumulator: AnthropicStreamAccumulator,
        emit: suspend (GatewayStreamEvent) -> Unit,
    ): Boolean {
        if (line.isEmpty()) return false
        if (!line.startsWith("data:")) return false
        val payload = line.removePrefix("data:").trim()
        if (payload.isEmpty()) return false
        return runCatching { accumulator.feed(payload, emit) }
            .getOrElse {
                logger.warn { "Skipping malformed Anthropic SSE chunk: $payload" }
                false
            }
    }

    override suspend fun availableModels(): List<String> {
        val response: HttpResponse = httpClient.get("$host/v1/models") {
            applyMessagesHeaders()
        }
        ensureSuccess(response)
        val body = response.bodyAsText()
        val parsed = runCatching { json.decodeFromString(AnthropicModelsListResponse.serializer(), body) }
            .getOrElse { throw LlmGatewayException("Failed to parse Anthropic model list response: $body", it) }
        return parsed.data.map { it.id }.sorted()
    }

    private suspend fun postMessages(request: AnthropicMessagesRequest): AnthropicMessagesResponse {
        val response: HttpResponse = httpClient.post("$host/v1/messages") {
            applyMessagesHeaders()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AnthropicMessagesRequest.serializer(), request))
        }
        ensureSuccess(response)
        val body = response.bodyAsText()
        return runCatching { json.decodeFromString(AnthropicMessagesResponse.serializer(), body) }
            .getOrElse { throw LlmGatewayException("Failed to parse Anthropic chat response: $body", it) }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyMessagesHeaders() {
        header("x-api-key", apiKey)
        header("anthropic-version", anthropicVersion)
    }

    private fun buildMessagesRequest(
        model: String,
        messages: List<LlmMessage>,
        tools: List<AnthropicTool>?,
        toolChoice: AnthropicToolChoice?,
        config: CompletionConfig,
        stream: Boolean,
    ): AnthropicMessagesRequest {
        if (config.reasoningEffort != null) {
            logger.warn {
                "AnthropicGateway does not yet support reasoning_effort=${config.reasoningEffort}; ignoring."
            }
        }
        val adapted = messages.toAnthropicMessages()
        return AnthropicMessagesRequest(
            model = model,
            maxTokens = config.maxTokens.takeIf { it > 0 } ?: DEFAULT_MAX_TOKENS,
            messages = adapted.messages,
            system = adapted.system,
            temperature = config.temperature,
            tools = tools,
            toolChoice = toolChoice,
            stream = stream.takeIf { it },
        )
    }

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status == HttpStatusCode.OK) return
        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        val parsed = runCatching { json.decodeFromString(AnthropicErrorResponse.serializer(), body) }
            .getOrNull()
        val message = parsed?.error?.message?.let { "Anthropic returned ${response.status}: $it" }
            ?: "Anthropic returned ${response.status}: $body"
        throw LlmGatewayException(message)
    }

    private fun AnthropicMessagesResponse.toGatewayResponse(): LlmGatewayResponse {
        val textPieces = content.filterIsInstance<AnthropicResponseContent.Text>()
            .map { it.text }
        val toolCalls = content.filterIsInstance<AnthropicResponseContent.ToolUse>()
            .map { LlmToolCall(id = it.id, name = it.name, arguments = it.input) }
        val thinking = content.filterIsInstance<AnthropicResponseContent.Thinking>()
            .joinToString(separator = "\n") { it.thinking }
            .takeIf { it.isNotEmpty() }
        return LlmGatewayResponse(
            content = textPieces.joinToString(separator = "").takeIf { it.isNotEmpty() },
            toolCalls = toolCalls,
            thinking = thinking,
        )
    }

    private fun buildHttpClient(engine: HttpClientEngine?): HttpClient {
        val configure: HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) { json(this@AnthropicGateway.json) }
            expectSuccess = false
        }
        return if (engine != null) HttpClient(engine, configure) else HttpClient(configure)
    }

    public companion object {
        public const val DEFAULT_MAX_TOKENS: Int = 4096

        public val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
            explicitNulls = false
            classDiscriminator = "type"
        }

        /**
         * Helper for tests / callers that want a stable JSON schema for a
         * trivial string-returning structured response. Not used by the gateway
         * itself.
         */
        @Suppress("unused")
        public fun trivialStringSchema(propertyName: String = "answer"): JsonObject = buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        propertyName,
                        buildJsonObject {
                            put("type", "string")
                        },
                    )
                },
            )
        }
    }
}
