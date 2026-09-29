package com.mojentic.omlx

import com.mojentic.openai.OpenAIGateway
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration

/**
 * A fake oMLX server on a Ktor `MockEngine`. It records every request and
 * answers each one with [respond].
 */
internal class OmlxTestServer(
    private val respond: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val requests = mutableListOf<HttpRequestData>()

    val engine: MockEngine = MockEngine { request ->
        requests += request
        respond(request)
    }

    /** The JSON body of the only request. */
    val body: JsonObject get() = bodyOf(requests.single())

    fun bodyOf(request: HttpRequestData): JsonObject = Json.parseToJsonElement((request.body as TextContent).text).jsonObject

    fun gateway(
        host: String? = null,
        apiKey: String? = null,
        timeout: Duration? = null,
        environment: Map<String, String> = emptyMap(),
    ): OmlxGateway = OmlxGateway(
        settings = OmlxSettings.resolve(host, apiKey, timeout) { environment[it] },
        engine = engine,
        json = OpenAIGateway.DEFAULT_JSON,
    )

    companion object {
        val JSON_HEADERS: Headers = headersOf(HttpHeaders.ContentType, "application/json")
        val SSE_HEADERS: Headers = headersOf(HttpHeaders.ContentType, "text/event-stream")

        /** A server that answers every request with [body], [status] and [headers]. */
        fun answering(
            body: String,
            status: HttpStatusCode = HttpStatusCode.OK,
            headers: Headers = JSON_HEADERS,
        ): OmlxTestServer = OmlxTestServer { respond(body, status, headers) }
    }
}
