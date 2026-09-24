package com.mojentic.openai

import com.mojentic.llm.LlmMessage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpenAIResponseEvidenceTest {
    private lateinit var gateway: OpenAIGateway

    private val usage = buildJsonObject {
        put("prompt_tokens", 10)
        put("completion_tokens", 4)
        put("total_tokens", 14)
        put("completion_tokens_details", buildJsonObject { put("reasoning_tokens", 0) })
    }

    @AfterTest
    fun tearDown() {
        if (::gateway.isInitialized) gateway.close()
    }

    private fun newGateway(body: String): OpenAIGateway = OpenAIGateway(
        apiKey = "test",
        engine = MockEngine {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    ).also { gateway = it }

    @Test
    fun completeReportsUsageProviderModelAndFinishReasonUnchanged() = runTest {
        val response = newGateway(
            """{"model":"gpt-4o-mini-2024-07-18","choices":[{"index":0,"finish_reason":"length",""" +
                """"message":{"role":"assistant","content":"hi"}}],"usage":$usage}""",
        ).complete("gpt-4o-mini", listOf(LlmMessage.user("hi")))

        assertEquals(usage, response.usage)
        assertEquals("gpt-4o-mini-2024-07-18", response.providerModel)
        assertEquals("length", response.finishReason)
    }

    @Test
    fun completeReportsNullUsageWhenProviderSendsNone() = runTest {
        val response = newGateway("""{"choices":[{"index":0,"message":{"role":"assistant","content":"hi"}}]}""")
            .complete("gpt-4o-mini", listOf(LlmMessage.user("hi")))

        assertNull(response.usage)
        assertNull(response.providerModel)
        assertNull(response.finishReason)
    }

    @Test
    fun structuredResponseCarriesEvidenceAndObject() = runTest {
        val response = newGateway(
            """{"model":"gpt-4o-2024-08-06","choices":[{"index":0,"finish_reason":"stop",""" +
                """"message":{"role":"assistant","content":"{\"a\":1}"}}],"usage":$usage}""",
        ).completeJsonResponse("gpt-4o", listOf(LlmMessage.user("hi")), buildJsonObject { put("type", "object") })

        assertEquals(buildJsonObject { put("a", JsonPrimitive(1)) }, response.structuredJson)
        assertEquals(usage, response.usage)
        assertEquals("gpt-4o-2024-08-06", response.providerModel)
        assertEquals("stop", response.finishReason)
    }
}
