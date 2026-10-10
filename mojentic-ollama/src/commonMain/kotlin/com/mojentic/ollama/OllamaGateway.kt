package com.mojentic.ollama

import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmGateway
import com.mojentic.llm.LlmGatewayResponse
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.LlmToolCall
import com.mojentic.llm.StreamErrorReason
import com.mojentic.llm.StreamEventsGateway
import com.mojentic.llm.recovery.RecoveryHttp
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryStreamClosedException
import com.mojentic.llm.recovery.RecoveryStreamException
import com.mojentic.llm.recovery.recoveryHttpClient
import com.mojentic.llm.tools.LlmTool
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readLine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val logger = KotlinLogging.logger {}

/**
 * Default Ollama HTTP host.
 */
public const val DEFAULT_OLLAMA_HOST: String = "http://localhost:11434"

/**
 * Ktor-Client backed gateway to a local or remote Ollama server.
 *
 * @param host Base URL of the Ollama HTTP API. Default is [DEFAULT_OLLAMA_HOST].
 * @param engine Optional Ktor engine override. Tests pass a `MockEngine` here.
 * @param json Json codec used for request / response (de)serialisation.
 */
public class OllamaGateway(
    private val host: String = DEFAULT_OLLAMA_HOST,
    engine: HttpClientEngine? = null,
    private val json: Json = DEFAULT_JSON,
) : LlmGateway,
    StreamEventsGateway {
    private val httpClient: HttpClient = buildHttpClient(engine)
    private val recoveryClientDelegate = lazy { recoveryHttpClient(engine) }
    private val recoveryClient: HttpClient by recoveryClientDelegate

    /**
     * Closes the underlying Ktor client. Call when you're done with the gateway —
     * apps that keep one gateway around for the process lifetime can skip this.
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
        val request = OllamaChatRequest(
            model = model,
            messages = messages.toOllamaMessages(),
            options = optionsFor(config),
            stream = false,
            tools = tools?.toOllamaTools(),
            format = config.responseFormat?.toOllamaFormat(),
            think = if (config.reasoningEffort != null) true else null,
        )
        val response = postChat(request, config.recovery)
        return LlmGatewayResponse(
            content = response.message.content,
            thinking = response.message.thinking,
            toolCalls = response.message.toolCalls.orEmpty().map { it.toLlmToolCall() },
            usage = response.reportedUsage,
            providerModel = response.model,
            finishReason = response.doneReason,
            metadata = response.reportedMetadata,
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
        val request = OllamaChatRequest(
            model = model,
            messages = messages.toOllamaMessages(),
            options = optionsFor(config),
            stream = false,
            format = schema,
            think = if (config.reasoningEffort != null) true else null,
        )
        val response = postChat(request, config.recovery, structured = true)
        val raw = response.message.content
            ?: throw LlmGatewayException("Ollama returned no content for structured-output request")
        val parsed = runCatching { json.parseToJsonElement(raw) }
            .getOrElse { throw LlmGatewayException("Ollama structured response was not valid JSON: $raw", it) }
        val structured = parsed as? JsonObject
            ?: throw LlmGatewayException("Ollama structured response was not a JSON object: $raw")
        return LlmGatewayResponse(
            content = raw,
            structuredJson = structured,
            usage = response.reportedUsage,
            providerModel = response.model,
            finishReason = response.doneReason,
            metadata = response.reportedMetadata,
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
            val request = OllamaChatRequest(
                model = model,
                messages = messages.toOllamaMessages(),
                options = optionsFor(config),
                stream = true,
                tools = tools?.toOllamaTools(),
                format = config.responseFormat?.toOllamaFormat(),
                think = if (config.reasoningEffort != null) true else null,
            )

            val statement = httpClient.preparePost("$host/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(OllamaChatRequest.serializer(), request))
            }
            statement.execute { httpResponse ->
                ensureSuccess(httpResponse)
                val channel = httpResponse.bodyAsChannel()
                while (true) {
                    val line = channel.readLine() ?: break
                    if (line.isBlank()) continue
                    val chunk = runCatching { json.decodeFromString(OllamaChatResponse.serializer(), line) }
                        .getOrElse {
                            logger.warn { "Skipping malformed Ollama stream chunk: $line" }
                            continue
                        }
                    chunk.message.content?.takeIf { it.isNotEmpty() }?.let {
                        send(GatewayStreamEvent.Content(it))
                    }
                    chunk.message.thinking?.takeIf { it.isNotEmpty() }?.let {
                        send(GatewayStreamEvent.Thinking(it))
                    }
                    chunk.message.toolCalls?.takeIf { it.isNotEmpty() }?.let { calls ->
                        send(GatewayStreamEvent.ToolCalls(calls.map { it.toLlmToolCall() }))
                    }
                }
            }
        }.buffer(Channel.RENDEZVOUS)
    }

    /**
     * Streams one turn as [CompletionStreamEvent]s for [com.mojentic.llm.LlmBroker.generateStreamEvents].
     *
     * Sends one request with no tools. Success needs a final frame with
     * `done: true` and `done_reason: "stop"`; Ollama servers too old to send
     * `done_reason` always end in an incomplete completion. Cancelling the
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
            val request = OllamaChatRequest(
                model = model,
                messages = messages.toOllamaMessages(),
                options = optionsFor(config),
                stream = true,
                format = config.responseFormat?.toOllamaFormat(),
                think = if (config.reasoningEffort != null) true else null,
            )

            val statement = httpClient.preparePost("$host/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(OllamaChatRequest.serializer(), request))
            }
            statement.execute { response ->
                if (!response.status.isSuccess()) {
                    val detail = response.bodyAsText()
                    send(CompletionStreamEvent.Error(StreamErrorReason.ProviderError(detail, response.status.value)))
                    return@execute
                }
                val parser = OllamaStreamEventParser(json)
                val channel = response.bodyAsChannel()
                while (!parser.isTerminal) {
                    val line = channel.readLine() ?: break
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
        val request = OllamaChatRequest(
            model = model,
            messages = messages.toOllamaMessages(),
            options = optionsFor(config),
            stream = true,
            tools = tools?.toOllamaTools(),
            format = config.responseFormat?.toOllamaFormat(),
            think = if (config.reasoningEffort != null) true else null,
        )

        RecoveryHttp(recoveryClient, "ollama", "$host/api/chat").executeStream(
            json.encodeToString(OllamaChatRequest.serializer(), request),
            "stream",
            requireNotNull(config.recovery),
        ) { stream ->
            var terminal = false
            val completedTools = mutableListOf<LlmToolCall>()
            while (!terminal) {
                val line = stream.readLine() ?: throw RecoveryStreamClosedException()
                if (line.isBlank()) continue
                val chunk = try {
                    json.decodeRecoveryFrame(line)
                } catch (cause: Exception) {
                    stream.invalid(cause)
                }
                completedTools += chunk.message.toolCalls.orEmpty().map { it.toLlmToolCall() }
                stream.progress(if (chunk.done && chunk.doneReason == "stop") completedTools.size else 0)
                if (chunk.done) stream.metrics(chunk.reportedUsage, chunk.reportedMetadata)
                emitRecoveryChunk(chunk, stream)
                if (chunk.done && chunk.doneReason != "stop") throw RecoveryStreamException()
                terminal = chunk.done
            }
            stream.finish()
            if (completedTools.isNotEmpty()) {
                stream.delivered(toolCalls = completedTools.size)
                emit(GatewayStreamEvent.ToolCalls(completedTools))
            }
        }
    }

    private fun recoveryStreamEvents(
        model: String,
        messages: List<LlmMessage>,
        config: CompletionConfig,
    ): Flow<CompletionStreamEvent> = flow {
        val request = OllamaChatRequest(
            model = model,
            messages = messages.toOllamaMessages(),
            options = optionsFor(config),
            stream = true,
            format = config.responseFormat?.toOllamaFormat(),
            think = if (config.reasoningEffort != null) true else null,
        )

        var completed: CompletionStreamEvent.Completed? = null
        RecoveryHttp(recoveryClient, "ollama", "$host/api/chat").executeStream(
            json.encodeToString(OllamaChatRequest.serializer(), request),
            "streamEvents",
            requireNotNull(config.recovery),
        ) { stream ->
            val parser = OllamaStreamEventParser(json)
            while (!parser.isTerminal) {
                val line = stream.readLine() ?: throw RecoveryStreamClosedException()
                if (line.isBlank()) continue
                val chunk = try {
                    json.decodeRecoveryFrame(line)
                } catch (cause: Exception) {
                    stream.invalid(cause)
                }
                val events = parser.accept(line)
                val error = events.filterIsInstance<CompletionStreamEvent.Error>().firstOrNull()
                if (error?.reason is StreamErrorReason.InvalidStreamEvent || error?.reason is StreamErrorReason.ProviderError) {
                    throw RecoveryStreamException(error.reason)
                }
                stream.progress()
                if (chunk.done) stream.metrics(chunk.reportedUsage, chunk.reportedMetadata)
                for (event in events) {
                    when (event) {
                        is CompletionStreamEvent.Error -> throw RecoveryStreamException(event.reason)

                        is CompletionStreamEvent.Content -> {
                            stream.delivered(content = event.text)
                            emit(event)
                        }

                        is CompletionStreamEvent.Completed -> {
                            stream.finish()
                            completed = event
                        }
                    }
                }
            }
        }
        emit(checkNotNull(completed))
    }.catch { failure -> emit(CompletionStreamEvent.Error(StreamErrorReason.RequestFailed(failure))) }

    override suspend fun availableModels(): List<String> {
        val response: HttpResponse = httpClient.get("$host/api/tags")
        ensureSuccess(response)
        val body = response.bodyAsText()
        val parsed = runCatching { json.decodeFromString(OllamaListResponse.serializer(), body) }
            .getOrElse { throw LlmGatewayException("Failed to parse Ollama model list response: $body", it) }
        return parsed.models.map { it.model }.sorted()
    }

    private suspend fun postChat(
        request: OllamaChatRequest,
        policy: RecoveryPolicy?,
        structured: Boolean = false,
    ): OllamaChatResponse {
        if (policy != null) {
            val payload = json.encodeToString(OllamaChatRequest.serializer(), request)
            return RecoveryHttp(recoveryClient, "ollama", "$host/api/chat").execute(
                payload,
                if (structured) "structured" else "complete",
                policy,
            ) { body, _ ->
                val parsed = json.decodeFromString(OllamaChatResponse.serializer(), body)
                if (structured) require(json.parseToJsonElement(requireNotNull(parsed.message.content)) is JsonObject)
                parsed
            }
        }
        val response: HttpResponse = httpClient.post("$host/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(OllamaChatRequest.serializer(), request))
        }
        ensureSuccess(response)
        val body = response.bodyAsText()
        return runCatching { json.decodeFromString(OllamaChatResponse.serializer(), body) }
            .getOrElse { throw LlmGatewayException("Failed to parse Ollama chat response: $body", it) }
    }

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status == HttpStatusCode.OK) return
        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        throw LlmGatewayException("Ollama returned ${response.status}: $body")
    }

    private fun optionsFor(config: CompletionConfig): OllamaOptions = OllamaOptions(
        temperature = config.temperature,
        numCtx = config.numCtx,
        numPredict = when {
            config.numPredict > 0 -> config.numPredict
            config.maxTokens > 0 -> config.maxTokens
            else -> null
        },
    )

    private fun buildHttpClient(engine: HttpClientEngine?): HttpClient {
        val configure: HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) { json(this@OllamaGateway.json) }
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
