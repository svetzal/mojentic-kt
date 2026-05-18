package com.mojentic.openai

import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmGateway
import com.mojentic.llm.LlmGatewayResponse
import com.mojentic.llm.LlmMessage
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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val logger = KotlinLogging.logger {}

/**
 * Default OpenAI HTTP host.
 */
public const val DEFAULT_OPENAI_HOST: String = "https://api.openai.com/v1"

/**
 * Ktor-Client backed gateway to the OpenAI chat-completions API.
 *
 * @param apiKey Bearer token. Required for the public OpenAI endpoint.
 * @param host Base URL of the OpenAI HTTP API. Default is [DEFAULT_OPENAI_HOST].
 *             Override for Azure OpenAI or other compatible endpoints.
 * @param engine Optional Ktor engine override. Tests pass a `MockEngine` here.
 * @param json Json codec used for request / response (de)serialisation.
 */
public class OpenAIGateway(
    private val apiKey: String,
    private val host: String = DEFAULT_OPENAI_HOST,
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
        val request = buildChatRequest(
            model = model,
            messages = messages,
            tools = tools,
            config = config,
            stream = false,
            responseFormat = null,
        )
        val response = postChat(request)
        val choice = response.choices.firstOrNull()
            ?: throw LlmGatewayException("OpenAI returned no choices in response")
        val message = choice.message ?: throw LlmGatewayException("OpenAI choice missing message")
        return LlmGatewayResponse(
            content = message.content,
            toolCalls = message.toolCalls.orEmpty().map { it.toLlmToolCall(json) },
            thinking = message.reasoningContent,
        )
    }

    override suspend fun completeJson(
        model: String,
        messages: List<LlmMessage>,
        schema: JsonObject,
        config: CompletionConfig,
    ): JsonObject {
        val request = buildChatRequest(
            model = model,
            messages = messages,
            tools = null,
            config = config,
            stream = false,
            responseFormat = OpenAIResponseFormat(
                type = "json_schema",
                jsonSchema = OpenAIJsonSchema(name = "structured_response", schema = schema, strict = false),
            ),
        )
        val response = postChat(request)
        val raw = response.choices.firstOrNull()?.message?.content
            ?: throw LlmGatewayException("OpenAI returned no content for structured-output request")
        val parsed = runCatching { json.parseToJsonElement(raw) }
            .getOrElse { throw LlmGatewayException("OpenAI structured response was not valid JSON: $raw", it) }
        return parsed as? JsonObject
            ?: throw LlmGatewayException("OpenAI structured response was not a JSON object: $raw")
    }

    override fun stream(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
    ): Flow<GatewayStreamEvent> = flow {
        val request = buildChatRequest(
            model = model,
            messages = messages,
            tools = tools,
            config = config,
            stream = true,
            responseFormat = null,
        )
        val httpResponse: HttpResponse = httpClient.post("$host/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(OpenAIChatRequest.serializer(), request))
        }
        ensureSuccess(httpResponse)
        val accumulator = StreamingToolCallAccumulator(json)
        val channel = httpResponse.bodyAsChannel()
        var stopped = false
        while (!stopped) {
            val line = channel.readLine() ?: break
            stopped = handleSseLine(line, accumulator) { event -> emit(event) }
        }
        val finalised = accumulator.toLlmToolCalls()
        if (finalised.isNotEmpty()) emit(GatewayStreamEvent.ToolCalls(finalised))
    }

    private suspend fun handleSseLine(
        line: String,
        accumulator: StreamingToolCallAccumulator,
        emit: suspend (GatewayStreamEvent) -> Unit,
    ): Boolean {
        val payload = ssePayload(line) ?: return false
        if (payload == "[DONE]") return true
        val delta = parseSseDelta(payload) ?: return false
        delta.content?.takeIf { it.isNotEmpty() }?.let { emit(GatewayStreamEvent.Content(it)) }
        delta.reasoningContent?.takeIf { it.isNotEmpty() }?.let { emit(GatewayStreamEvent.Thinking(it)) }
        delta.toolCalls?.forEach { accumulator.append(it) }
        return false
    }

    private fun parseSseDelta(payload: String): OpenAIResponseMessage? {
        val chunk = runCatching { json.decodeFromString(OpenAIChatResponse.serializer(), payload) }
            .getOrElse {
                logger.warn { "Skipping malformed OpenAI SSE chunk: $payload" }
                null
            }
        return chunk?.choices?.firstOrNull()?.delta
    }

    private fun ssePayload(line: String): String? {
        if (line.isEmpty()) return null
        if (!line.startsWith("data:")) return null
        return line.removePrefix("data:").trim()
    }

    private fun buildChatRequest(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
        stream: Boolean,
        responseFormat: OpenAIResponseFormat?,
    ): OpenAIChatRequest {
        val reasoning = OpenAIModelRegistry.supportsReasoningEffort(model)
        return OpenAIChatRequest(
            model = model,
            messages = messages.toOpenAIMessages(),
            temperature = if (reasoning) null else config.temperature,
            maxTokens = if (reasoning) null else config.maxTokens.takeIf { it > 0 },
            maxCompletionTokens = if (reasoning) config.maxTokens.takeIf { it > 0 } else null,
            stream = stream,
            tools = tools?.toOpenAITools(),
            responseFormat = responseFormat,
            reasoningEffort = reasoningEffortFor(config, model),
        )
    }

    override suspend fun availableModels(): List<String> {
        val response: HttpResponse = httpClient.get("$host/models") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
        }
        ensureSuccess(response)
        val body = response.bodyAsText()
        val parsed = runCatching { json.decodeFromString(OpenAIListResponse.serializer(), body) }
            .getOrElse { throw LlmGatewayException("Failed to parse OpenAI model list response: $body", it) }
        return parsed.data.map { it.id }.sorted()
    }

    private suspend fun postChat(request: OpenAIChatRequest): OpenAIChatResponse {
        val response: HttpResponse = httpClient.post("$host/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(OpenAIChatRequest.serializer(), request))
        }
        ensureSuccess(response)
        val body = response.bodyAsText()
        return runCatching { json.decodeFromString(OpenAIChatResponse.serializer(), body) }
            .getOrElse { throw LlmGatewayException("Failed to parse OpenAI chat response: $body", it) }
    }

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status == HttpStatusCode.OK) return
        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        throw LlmGatewayException("OpenAI returned ${response.status}: $body")
    }

    private fun reasoningEffortFor(config: CompletionConfig, model: String): String? {
        if (!OpenAIModelRegistry.supportsReasoningEffort(model)) return null
        return config.reasoningEffort?.wireValue
    }

    private fun buildHttpClient(engine: HttpClientEngine?): HttpClient {
        val configure: HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) { json(this@OpenAIGateway.json) }
            expectSuccess = false
        }
        return if (engine != null) HttpClient(engine, configure) else HttpClient(configure)
    }

    public companion object {
        public val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
            explicitNulls = false
        }
    }
}
