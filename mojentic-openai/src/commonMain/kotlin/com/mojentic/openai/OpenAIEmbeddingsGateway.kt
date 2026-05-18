package com.mojentic.openai

import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.EmbeddingsGateway
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * OpenAI embeddings gateway — thin wrapper around `POST /v1/embeddings`.
 */
public class OpenAIEmbeddingsGateway(
    private val apiKey: String,
    private val host: String = DEFAULT_OPENAI_HOST,
    engine: HttpClientEngine? = null,
    private val json: Json = OpenAIGateway.DEFAULT_JSON,
) : EmbeddingsGateway {
    private val httpClient: HttpClient = buildHttpClient(engine)

    public fun close() {
        httpClient.close()
    }

    override suspend fun embed(model: String, text: String): FloatArray =
        embedBatch(model, listOf(text)).first()

    override suspend fun embedBatch(model: String, texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val request = OpenAIEmbeddingsRequest(model = model, input = texts)
        val response: HttpResponse = httpClient.post("$host/embeddings") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(OpenAIEmbeddingsRequest.serializer(), request))
        }
        if (response.status != HttpStatusCode.OK) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            throw LlmGatewayException("OpenAI embeddings returned ${response.status}: $body")
        }
        val body = response.bodyAsText()
        val parsed = runCatching { json.decodeFromString(OpenAIEmbeddingsResponse.serializer(), body) }
            .getOrElse { throw LlmGatewayException("Failed to parse OpenAI embeddings response: $body", it) }
        return parsed.data
            .sortedBy { it.index }
            .map { entry -> FloatArray(entry.embedding.size) { i -> entry.embedding[i] } }
    }

    private fun buildHttpClient(engine: HttpClientEngine?): HttpClient {
        val configure: HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) { json(this@OpenAIEmbeddingsGateway.json) }
            expectSuccess = false
        }
        return if (engine != null) HttpClient(engine, configure) else HttpClient(configure)
    }
}
