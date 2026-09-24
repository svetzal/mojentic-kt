package com.mojentic.ollama

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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OllamaResponseFormatTest {
    private lateinit var gateway: OllamaGateway
    private val bodies = mutableListOf<JsonObject>()

    private val schema = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { put("answer", buildJsonObject { put("type", "string") }) })
    }

    private val doneFrame = """{"model":"m","message":{"role":"assistant","content":"{}"},"done":true,"done_reason":"stop"}"""

    @AfterTest
    fun tearDown() {
        if (::gateway.isInitialized) gateway.close()
    }

    private fun newGateway(): OllamaGateway = OllamaGateway(
        engine = MockEngine { request ->
            bodies += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            respond(doneFrame + "\n", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/x-ndjson"))
        },
    ).also { gateway = it }

    private suspend fun streamedRequest(format: ResponseFormat?): JsonObject {
        newGateway().stream("m", listOf(LlmMessage.user("hi")), config = CompletionConfig(responseFormat = format)).toList()
        return bodies.single()
    }

    private suspend fun completedRequest(format: ResponseFormat?): JsonObject {
        newGateway().complete("m", listOf(LlmMessage.user("hi")), config = CompletionConfig(responseFormat = format))
        return bodies.single()
    }

    @Test
    fun streamingRequestOmitsFormatWhenAbsent() = runTest {
        val body = streamedRequest(null)

        assertFalse("format" in body, "unexpected format in $body")
    }

    @Test
    fun streamingRequestOmitsFormatForText() = runTest {
        val body = streamedRequest(ResponseFormat.Text)

        assertFalse("format" in body, "text must not send format: $body")
    }

    @Test
    fun streamingRequestSendsJsonForJsonObjectFormat() = runTest {
        assertEquals(JsonPrimitive("json"), streamedRequest(ResponseFormat.Json())["format"])
    }

    @Test
    fun streamingRequestSendsSchemaForJsonSchemaFormat() = runTest {
        assertEquals(schema, streamedRequest(ResponseFormat.Json(schema))["format"])
    }

    @Test
    fun completeRequestSendsSchemaForJsonSchemaFormat() = runTest {
        assertEquals(schema, completedRequest(ResponseFormat.Json(schema))["format"])
    }

    @Test
    fun completeRequestOmitsFormatWhenAbsent() = runTest {
        val body = completedRequest(null)

        assertFalse("format" in body, "unexpected format in $body")
    }
}
