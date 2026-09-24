package com.mojentic.openai

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.ResponseFormat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OpenAIResponseFormatTest {
    private lateinit var gateway: OpenAIGateway
    private val bodies = mutableListOf<JsonObject>()

    private val schema = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { put("answer", buildJsonObject { put("type", "string") }) })
    }

    @AfterTest
    fun tearDown() {
        if (::gateway.isInitialized) gateway.close()
    }

    private fun newGateway(responseBody: String): OpenAIGateway = OpenAIGateway(
        apiKey = "test",
        engine = MockEngine { request ->
            val text = (request.body as TextContent).text
            bodies += Json.parseToJsonElement(text).jsonObject
            respond(responseBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    ).also { gateway = it }

    private suspend fun streamedRequest(format: ResponseFormat?): JsonObject {
        newGateway("data: [DONE]\n\n")
            .stream("gpt-4o-mini", listOf(LlmMessage.user("hi")), config = CompletionConfig(responseFormat = format))
            .toList()
        return bodies.single()
    }

    private suspend fun completedRequest(format: ResponseFormat?): JsonObject {
        newGateway("""{"choices":[{"index":0,"message":{"role":"assistant","content":"{}"}}]}""")
            .complete("gpt-4o-mini", listOf(LlmMessage.user("hi")), config = CompletionConfig(responseFormat = format))
        return bodies.single()
    }

    private fun format(type: String): JsonObject = buildJsonObject { put("type", type) }

    private val jsonSchemaFormat: JsonObject
        get() = buildJsonObject {
            put("type", "json_schema")
            put(
                "json_schema",
                buildJsonObject {
                    put("name", "response")
                    put("schema", schema)
                },
            )
        }

    @Test
    fun streamingRequestOmitsResponseFormatWhenAbsent() = runTest {
        val body = streamedRequest(null)

        assertFalse("response_format" in body, "unexpected response_format in $body")
    }

    @Test
    fun streamingRequestForwardsTextFormat() = runTest {
        assertEquals(format("text"), streamedRequest(ResponseFormat.Text)["response_format"])
    }

    @Test
    fun streamingRequestForwardsJsonObjectFormat() = runTest {
        assertEquals(format("json_object"), streamedRequest(ResponseFormat.Json())["response_format"])
    }

    @Test
    fun streamingRequestForwardsJsonSchemaFormat() = runTest {
        assertEquals(jsonSchemaFormat, streamedRequest(ResponseFormat.Json(schema))["response_format"])
    }

    @Test
    fun legacyStreamingRequestDoesNotAskForUsage() = runTest {
        val body = streamedRequest(null)

        assertFalse("stream_options" in body, "legacy stream must not request usage: $body")
    }

    @Test
    fun completeRequestOmitsResponseFormatWhenAbsent() = runTest {
        val body = completedRequest(null)

        assertFalse("response_format" in body, "unexpected response_format in $body")
    }

    @Test
    fun completeRequestForwardsJsonSchemaFormat() = runTest {
        assertEquals(jsonSchemaFormat, completedRequest(ResponseFormat.Json(schema))["response_format"])
    }

    @Test
    fun completeRequestForwardsJsonObjectFormat() = runTest {
        assertEquals(format("json_object"), completedRequest(ResponseFormat.Json())["response_format"])
    }
}
