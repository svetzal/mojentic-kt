package com.mojentic.omlx

import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.EmbeddingsGateway
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmGateway
import com.mojentic.llm.LlmGatewayResponse
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.ResponseFormat
import com.mojentic.llm.StreamErrorReason
import com.mojentic.llm.StreamEventsGateway
import com.mojentic.llm.recovery.RecoveryHttp
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.recoveryHttpClient
import com.mojentic.llm.tools.LlmTool
import com.mojentic.openai.OpenAIChatRequest
import com.mojentic.openai.OpenAIChatResponse
import com.mojentic.openai.OpenAIEmbeddingsResponse
import com.mojentic.openai.OpenAIGateway
import com.mojentic.openai.OpenAILegacyStreamParser
import com.mojentic.openai.OpenAIListResponse
import com.mojentic.openai.OpenAIResponseFormat
import com.mojentic.openai.OpenAIStreamEventParser
import com.mojentic.openai.OpenAIStreamOptions
import com.mojentic.openai.toLlmToolCall
import com.mojentic.openai.toOpenAIMessages
import com.mojentic.openai.toOpenAIResponseFormat
import com.mojentic.openai.toOpenAITools
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

/**
 * Default oMLX host. The gateway adds `/v1` to every request path.
 */
public const val DEFAULT_OMLX_HOST: String = "http://localhost:8000"

/**
 * Default timeout for every oMLX request, including model load: 10 minutes
 * (`OMLX_TIMEOUT=600000`).
 */
public val DEFAULT_OMLX_TIMEOUT: Duration = 10.minutes

private const val RESPONSE_FORMAT_WARNING = "response_format_warning"

/**
 * Ktor-Client backed gateway to [oMLX](https://github.com/jundot/omlx), an LLM
 * server for Apple Silicon.
 *
 * oMLX speaks the OpenAI chat-completions protocol. This gateway reuses the
 * OpenAI gateway's message adapter and stream parsers, but not its model
 * registry: every configured parameter reaches the server unchanged, for any
 * model name.
 *
 * ## Configuration
 *
 * Each setting resolves as: the constructor argument, then the environment
 * variable, then the default. Environment variables are read on the JVM only.
 * On Android and iOS, pass explicit values.
 *
 * | Setting | Argument | Environment | Default |
 * |---|---|---|---|
 * | Host | `host` | `OMLX_HOST` | [DEFAULT_OMLX_HOST] |
 * | API key | `apiKey` | `OMLX_API_KEY` | none |
 * | Timeout | `timeout` | `OMLX_TIMEOUT` (milliseconds) | [DEFAULT_OMLX_TIMEOUT], 10 minutes |
 *
 * The host has no `/v1` suffix; the gateway adds it to every path. With an API
 * key, requests carry `Authorization: Bearer <key>`. Without one, or with a
 * blank key, they carry no authorization header.
 *
 * One timeout applies to every request, including [loadModel]. It bounds
 * connecting and each wait for data, so it also bounds a non-streaming reply,
 * but not a whole streamed response that keeps sending. The default is longer
 * than the other gateways use because local models are slow: a 16384-token
 * reply at 16 tokens a second takes 17 minutes.
 *
 * ## Chat requests
 *
 * Requests send `model`, `messages`, `temperature` and `max_tokens` (never
 * `max_completion_tokens`) from the config, and `reasoning_effort`,
 * `response_format` and `tools` when they are set. [CompletionConfig.numCtx] and
 * [CompletionConfig.numPredict] are not sent, because oMLX sets the context
 * length per model.
 *
 * `reasoning_effort` is sent as `low`, `medium` or `high`. It goes to the
 * model's chat template, so its effect depends on the model. A null [CompletionConfig.reasoningEffort] leaves the model's
 * default, and Qwen 3 models think by default. The model's reasoning arrives
 * in [LlmGatewayResponse.thinking].
 *
 * ## Truncation
 *
 * When `max_tokens` ends generation during thinking, a non-streaming response
 * puts the partial reasoning in `content`, `thinking` is null, and the finish
 * reason is `length`. A streaming response keeps it in the reasoning deltas.
 * The gateway maps fields as the server sends them. `content` is not an answer
 * unless [LlmGatewayResponse.finishReason] is `stop`.
 *
 * ## Structured output
 *
 * [completeJson] and [completeJsonResponse] send `response_format` with type
 * `json_schema` and the schema named `response`. When oMLX cannot compile a
 * grammar for a JSON format, it falls back to instructions in the prompt and
 * says so in a `Warning` response header. On a structured request (those two
 * methods, or [CompletionConfig.responseFormat] set to [ResponseFormat.Json]),
 * the gateway puts the header value in [LlmGatewayResponse.metadata] under
 * `response_format_warning`, joining several headers with `, `, and logs a
 * warning. It ignores the header for text or absent formats. It does not
 * retry or fail. Validate the content yourself.
 *
 * ## Streaming
 *
 * Both [stream] and [streamEvents] drop oMLX keep-alive frames (frames whose
 * `model` is `keepalive`) before parsing. [stream] surfaces reasoning deltas
 * as [GatewayStreamEvent.Thinking]; [streamEvents] yields no event for them.
 *
 * ## Errors
 *
 * A non-2xx response is an [LlmGatewayException] whose message carries the
 * HTTP status and the response body unchanged, for example an unknown model
 * (404) or unloading a model that is not loaded (400). In [streamEvents] it is
 * a [StreamErrorReason.ProviderError] with the status.
 */
public class OmlxGateway internal constructor(
    private val settings: OmlxSettings,
    engine: HttpClientEngine?,
    private val json: Json,
) : LlmGateway,
    StreamEventsGateway,
    EmbeddingsGateway {
    /**
     * Creates a gateway. Each argument left null falls back to its environment
     * variable (JVM only), then to its default.
     *
     * @param host oMLX base URL without `/v1`. Falls back to `OMLX_HOST`, then [DEFAULT_OMLX_HOST].
     * @param apiKey Bearer token. Falls back to `OMLX_API_KEY`. Blank sends no authorization header.
     * @param timeout Timeout for every request. Falls back to `OMLX_TIMEOUT` in milliseconds, then
     *                [DEFAULT_OMLX_TIMEOUT].
     * @param engine Optional Ktor engine override. Tests pass a `MockEngine` here.
     * @param json Json codec used for request / response (de)serialisation.
     * @throws IllegalArgumentException if [timeout] is not positive.
     */
    public constructor(
        host: String? = null,
        apiKey: String? = null,
        timeout: Duration? = null,
        engine: HttpClientEngine? = null,
        json: Json = OpenAIGateway.DEFAULT_JSON,
    ) : this(OmlxSettings.resolve(host, apiKey, timeout, ::environmentVariable), engine, json)

    private val httpClient: HttpClient = buildHttpClient(engine)
    private val recoveryClientDelegate = lazy { recoveryHttpClient(engine, settings.timeout.inWholeMilliseconds) }
    private val recoveryClient: HttpClient by recoveryClientDelegate

    /**
     * Closes the underlying Ktor client.
     */
    public fun close() {
        httpClient.close()
        if (recoveryClientDelegate.isInitialized()) recoveryClient.close()
    }

    override suspend fun complete(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
    ): LlmGatewayResponse {
        val request = chatRequest(model, messages, tools, config, stream = false)
        val (response, warning) = postChat(request, structured = config.responseFormat is ResponseFormat.Json, policy = config.recovery)
        val choice = response.choices.firstOrNull() ?: throw LlmGatewayException("oMLX returned no choices in response")
        val message = choice.message ?: throw LlmGatewayException("oMLX choice missing message")
        return LlmGatewayResponse(
            content = message.content,
            toolCalls = message.toolCalls.orEmpty().map { it.toLlmToolCall(json) },
            thinking = message.reasoningContent,
            usage = response.usage,
            providerModel = response.model,
            finishReason = choice.finishReason,
            metadata = warningMetadata(warning),
        )
    }

    override suspend fun completeJson(
        model: String,
        messages: List<LlmMessage>,
        schema: JsonObject,
        config: CompletionConfig,
    ): JsonObject = completeJsonResponse(model, messages, schema, config).structuredJson as JsonObject

    override suspend fun completeJsonResponse(
        model: String,
        messages: List<LlmMessage>,
        schema: JsonObject,
        config: CompletionConfig,
    ): LlmGatewayResponse {
        val request = chatRequest(
            model = model,
            messages = messages,
            tools = null,
            config = config,
            stream = false,
            responseFormat = ResponseFormat.Json(schema).toOpenAIResponseFormat(),
        )
        val (response, warning) = postChat(request, structured = true, policy = config.recovery)
        val choice = response.choices.firstOrNull()
        val raw = choice?.message?.content
            ?: throw LlmGatewayException("oMLX returned no content for structured-output request")
        val parsed = runCatching { json.parseToJsonElement(raw) }
            .getOrElse { throw LlmGatewayException("oMLX structured response was not valid JSON: $raw", it) }
        val structured = parsed as? JsonObject
            ?: throw LlmGatewayException("oMLX structured response was not a JSON object: $raw")
        return LlmGatewayResponse(
            content = raw,
            thinking = choice.message.reasoningContent,
            structuredJson = structured,
            usage = response.usage,
            providerModel = response.model,
            finishReason = choice.finishReason,
            metadata = warningMetadata(warning),
        )
    }

    override fun stream(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
    ): Flow<GatewayStreamEvent> = if (config.recovery != null) {
        recoveryStream(model, messages, tools, config)
    } else {
        channelFlow {
            chatStatement(chatRequest(model, messages, tools, config, stream = true)).execute { response ->
                ensureSuccess(response)
                val parser = OpenAILegacyStreamParser(json)
                val channel = response.bodyAsChannel()
                while (!parser.isDone) {
                    val line = channel.readLine() ?: break
                    if (isKeepAliveFrame(line, json)) continue
                    parser.accept(line).forEach { send(it) }
                }
                parser.finish().forEach { send(it) }
            }
        }.buffer(Channel.RENDEZVOUS)
    }

    /**
     * Streams one turn as [CompletionStreamEvent]s for [com.mojentic.llm.LlmBroker.generateStreamEvents].
     *
     * Sends one request with `stream_options.include_usage` set and no tools.
     * Keep-alive frames are dropped, so they never report `keepalive` as the
     * provider model. Reasoning deltas yield no events. Success needs
     * `finish_reason: "stop"` and the `[DONE]` marker. Cancelling the
     * collector cancels the request.
     */
    override fun streamEvents(
        model: String,
        messages: List<LlmMessage>,
        config: CompletionConfig,
    ): Flow<CompletionStreamEvent> = if (config.recovery != null) {
        recoveryStreamEvents(model, messages, config)
    } else {
        channelFlow {
            val request = chatRequest(model, messages, tools = null, config = config, stream = true)
                .copy(streamOptions = OpenAIStreamOptions(includeUsage = true))

            chatStatement(request).execute { response ->
                if (!response.status.isSuccess()) {
                    send(CompletionStreamEvent.Error(StreamErrorReason.ProviderError(response.bodyAsText(), response.status.value)))
                    return@execute
                }
                val parser = OpenAIStreamEventParser(json)
                val channel = response.bodyAsChannel()
                while (!parser.isTerminal) {
                    val line = channel.readLine() ?: break
                    if (isKeepAliveFrame(line, json)) continue
                    parser.accept(line).forEach { send(it) }
                }
                if (!parser.isTerminal) send(parser.endOfStream())
            }
        }.buffer(Channel.RENDEZVOUS)
            .catch { failure -> emit(CompletionStreamEvent.Error(StreamErrorReason.RequestFailed(failure))) }
    }

    private fun recoveryStream(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
    ): Flow<GatewayStreamEvent> = flow {
        val request = chatRequest(model, messages, tools, config, stream = true)
            .copy(streamOptions = OpenAIStreamOptions(includeUsage = true))
        RecoveryHttp(recoveryClient, "omlx", "${settings.baseUrl}/chat/completions", settings.apiKey).executeStream(
            json.encodeToString(OpenAIChatRequest.serializer(), request),
            "stream",
            requireNotNull(config.recovery),
        ) { stream ->
            OmlxRecoveryStreamParser(json).consume(stream, allowTools = true) { emit(it) }
        }
    }

    private fun recoveryStreamEvents(
        model: String,
        messages: List<LlmMessage>,
        config: CompletionConfig,
    ): Flow<CompletionStreamEvent> = flow {
        val request = chatRequest(model, messages, tools = null, config = config, stream = true)
            .copy(streamOptions = OpenAIStreamOptions(includeUsage = true))

        var completed: CompletionStreamEvent.Completed? = null
        RecoveryHttp(recoveryClient, "omlx", "${settings.baseUrl}/chat/completions", settings.apiKey).executeStream(
            json.encodeToString(OpenAIChatRequest.serializer(), request),
            "streamEvents",
            requireNotNull(config.recovery),
        ) { stream ->
            val evidence = OmlxRecoveryStreamParser(json).consume(stream, allowTools = false) { event ->
                if (event is GatewayStreamEvent.Content) emit(CompletionStreamEvent.Content(event.text))
            }
            completed = CompletionStreamEvent.Completed(evidence)
        }
        emit(checkNotNull(completed))
    }.catch { failure -> emit(CompletionStreamEvent.Error(StreamErrorReason.RequestFailed(failure))) }

    /**
     * Lists the models oMLX serves, from `GET /v1/models`, sorted.
     *
     * @throws LlmGatewayException on a non-2xx response or an unreadable body.
     */
    override suspend fun availableModels(): List<String> {
        val response = httpClient.get("${settings.baseUrl}/models") { authorize() }
        ensureSuccess(response)
        val body = response.bodyAsText()
        val parsed = runCatching { json.decodeFromString(OpenAIListResponse.serializer(), body) }
            .getOrElse { throw LlmGatewayException("Failed to parse oMLX model list response: $body", it) }
        return parsed.data.map { it.id }.sorted()
    }

    /**
     * Loads [model] into memory ahead of its first request, with `POST /v1/models/{id}/load`.
     *
     * Returns when the model is in memory. Loading a large model from a cold
     * disk takes a while; the gateway timeout covers it. A chat request loads
     * its model automatically; this is for warming up. oMLX has no pull
     * operation: it downloads models only through its admin dashboard.
     *
     * @throws IllegalArgumentException if [model] is blank, before any request.
     * @throws LlmGatewayException on a non-2xx response, for example an unknown model.
     */
    public suspend fun loadModel(model: String) {
        postModelAction(model, "load")
    }

    /**
     * Unloads [model] from memory, with `POST /v1/models/{id}/unload`.
     *
     * Returns when the model is out of memory.
     *
     * @throws IllegalArgumentException if [model] is blank, before any request.
     * @throws LlmGatewayException on a non-2xx response. Unloading a model that
     *         is not loaded is a 400 `invalid_request_error`.
     */
    public suspend fun unloadModel(model: String) {
        postModelAction(model, "unload")
    }

    /**
     * Embeds [text] with one `POST /v1/embeddings` request and returns `data[0].embedding`.
     *
     * The text is sent whole, with no client-side chunking or tokenizer. oMLX
     * has no standard embedding model, so [model] is required. A chat model is
     * a provider error (400).
     *
     * @throws IllegalArgumentException if [model] is blank, before any request.
     * @throws LlmGatewayException on a non-2xx response or a response with no embedding.
     */
    override suspend fun embed(model: String, text: String): FloatArray {
        requireEmbeddingModel(model)
        val response = httpClient.post("${settings.baseUrl}/embeddings") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(OmlxEmbeddingsRequest.serializer(), OmlxEmbeddingsRequest(model, text)))
        }
        ensureSuccess(response)
        val body = response.bodyAsText()
        val parsed = runCatching { json.decodeFromString(OpenAIEmbeddingsResponse.serializer(), body) }
            .getOrElse { throw LlmGatewayException("Failed to parse oMLX embeddings response: $body", it) }
        val embedding = parsed.data.firstOrNull()?.embedding
            ?: throw LlmGatewayException("oMLX embeddings response had no data: $body")
        return embedding.toFloatArray()
    }

    /**
     * Embeds each of [texts] with its own request, in order. See [embed].
     *
     * @throws IllegalArgumentException if [model] is blank, before any request.
     */
    override suspend fun embedBatch(model: String, texts: List<String>): List<FloatArray> {
        requireEmbeddingModel(model)
        return texts.map { embed(model, it) }
    }

    private fun requireEmbeddingModel(model: String) {
        require(model.isNotBlank()) { "oMLX embeddings require a model; there is no default embedding model" }
    }

    private fun chatRequest(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
        stream: Boolean,
        responseFormat: OpenAIResponseFormat? = config.responseFormat?.toOpenAIResponseFormat(),
    ): OpenAIChatRequest = OpenAIChatRequest(
        model = model,
        messages = messages.toOpenAIMessages(),
        temperature = config.temperature,
        maxTokens = config.maxTokens,
        stream = stream,
        tools = tools?.toOpenAITools(),
        responseFormat = responseFormat,
        reasoningEffort = config.reasoningEffort?.wireValue,
    )

    private suspend fun chatStatement(request: OpenAIChatRequest): HttpStatement =
        httpClient.preparePost("${settings.baseUrl}/chat/completions") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(OpenAIChatRequest.serializer(), request))
        }

    /** Posts a non-streaming chat request. Returns the parsed body and, for a structured request, any `Warning` header. */
    private suspend fun postChat(
        request: OpenAIChatRequest,
        structured: Boolean,
        policy: RecoveryPolicy?,
    ): Pair<OpenAIChatResponse, String?> {
        if (policy != null) {
            val payload = json.encodeToString(OpenAIChatRequest.serializer(), request)
            return RecoveryHttp(recoveryClient, "omlx", "${settings.baseUrl}/chat/completions", settings.apiKey).execute(
                payload,
                if (structured) "structured" else "complete",
                policy,
            ) { body, headers ->
                val parsed = json.decodeFromString(OpenAIChatResponse.serializer(), body)
                val message = requireNotNull(parsed.choices.firstOrNull()?.message)
                if (structured) require(json.parseToJsonElement(requireNotNull(message.content)) is JsonObject)
                val warning = if (structured) {
                    headers.entries.firstOrNull {
                        it.key.equals(HttpHeaders.Warning, ignoreCase = true)
                    }?.value?.joinToString(", ")
                } else {
                    null
                }
                parsed to warning
            }
        }
        val response = httpClient.post("${settings.baseUrl}/chat/completions") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(OpenAIChatRequest.serializer(), request))
        }
        ensureSuccess(response)
        val body = response.bodyAsText()
        val parsed = runCatching { json.decodeFromString(OpenAIChatResponse.serializer(), body) }
            .getOrElse { throw LlmGatewayException("Failed to parse oMLX chat response: $body", it) }
        val warning = if (structured) response.headers.getAll(HttpHeaders.Warning)?.joinToString(", ") else null
        warning?.let { logger.warn { "oMLX did not enforce the requested response format: $it" } }
        return parsed to warning
    }

    private suspend fun postModelAction(model: String, action: String) {
        require(model.isNotBlank()) { "oMLX model id must not be blank" }
        val response = httpClient.post("${settings.baseUrl}/models/${model.encodeURLParameter()}/$action") { authorize() }
        ensureSuccess(response)
    }

    private fun warningMetadata(warning: String?): JsonObject? =
        warning?.let { buildJsonObject { put(RESPONSE_FORMAT_WARNING, JsonPrimitive(it)) } }

    private fun HttpRequestBuilder.authorize() {
        settings.apiKey?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status.isSuccess()) return
        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        throw LlmGatewayException("oMLX returned ${response.status}: $body")
    }

    private fun buildHttpClient(engine: HttpClientEngine?): HttpClient {
        val configure: HttpClientConfig<*>.() -> Unit = {
            install(HttpTimeout) {
                connectTimeoutMillis = settings.timeout.inWholeMilliseconds
                socketTimeoutMillis = settings.timeout.inWholeMilliseconds
            }
            expectSuccess = false
        }
        return if (engine != null) HttpClient(engine, configure) else HttpClient(configure)
    }
}
