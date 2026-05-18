package com.mojentic.websearch.serpapi

import com.mojentic.errors.WebSearchGatewayException
import com.mojentic.llm.tools.websearch.WebSearchGateway
import com.mojentic.llm.tools.websearch.WebSearchResult
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Default SerpApi base URL. */
public const val DEFAULT_SERPAPI_HOST: String = "https://serpapi.com"

/**
 * [WebSearchGateway] backed by [SerpApi](https://serpapi.com).
 *
 * @param apiKey SerpApi key. The free tier rate-limits aggressively — production
 *   deployments should provide a paid key.
 * @param engine optional Ktor engine override (tests inject a `MockEngine`).
 * @param host overrideable base URL (defaults to [DEFAULT_SERPAPI_HOST]).
 * @param engineName the SerpApi `engine` parameter — defaults to `google`.
 */
public class SerpApiWebSearchGateway(
    private val apiKey: String,
    engine: HttpClientEngine? = null,
    private val host: String = DEFAULT_SERPAPI_HOST,
    private val engineName: String = "google",
    private val json: Json = DEFAULT_JSON,
) : WebSearchGateway {

    private val httpClient: HttpClient = buildHttpClient(engine)

    public fun close() {
        httpClient.close()
    }

    override suspend fun search(query: String, locale: String?): List<WebSearchResult> {
        val response: HttpResponse = httpClient.get("$host/search.json") {
            parameter("api_key", apiKey)
            parameter("engine", engineName)
            parameter("q", query)
            locale?.let { parameter("hl", it) }
        }
        ensureSuccess(response)
        val body = response.bodyAsText()
        val parsed = runCatching { json.decodeFromString(SerpApiResponse.serializer(), body) }
            .getOrElse { throw WebSearchGatewayException("Failed to parse SerpApi response: $body", it) }
        return parsed.organicResults.orEmpty().map {
            WebSearchResult(
                title = it.title.orEmpty(),
                link = it.link.orEmpty(),
                snippet = it.snippet.orEmpty(),
            )
        }
    }

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status == HttpStatusCode.OK) return
        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        throw WebSearchGatewayException("SerpApi returned ${response.status}: $body")
    }

    private fun buildHttpClient(engine: HttpClientEngine?): HttpClient {
        val configure: HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) { json(this@SerpApiWebSearchGateway.json) }
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

@Serializable
internal data class SerpApiResponse(
    @SerialName("organic_results")
    val organicResults: List<SerpApiOrganicResult>? = null,
)

@Serializable
internal data class SerpApiOrganicResult(
    val title: String? = null,
    val link: String? = null,
    val snippet: String? = null,
)
