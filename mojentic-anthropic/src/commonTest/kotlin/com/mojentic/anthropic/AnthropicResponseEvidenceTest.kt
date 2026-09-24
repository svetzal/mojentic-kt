package com.mojentic.anthropic

import com.mojentic.llm.LlmMessage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AnthropicResponseEvidenceTest {
    private lateinit var gateway: AnthropicGateway

    @AfterTest
    fun tearDown() {
        if (::gateway.isInitialized) gateway.close()
    }

    @Test
    fun completeReportsUsageProviderModelAndStopReasonUnchanged() = runTest {
        gateway = AnthropicGateway(
            apiKey = "test",
            engine = MockEngine {
                respond(
                    """{"id":"m_1","model":"claude-sonnet-4-5-20250929","content":[{"type":"text","text":"hi"}],""" +
                        """"stop_reason":"max_tokens","usage":{"input_tokens":5,"output_tokens":7,""" +
                        """"cache_read_input_tokens":2}}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )

        val response = gateway.complete("claude-sonnet-4-5", listOf(LlmMessage.user("hi")))

        assertEquals(
            buildJsonObject {
                put("input_tokens", 5)
                put("output_tokens", 7)
                put("cache_read_input_tokens", 2)
            },
            response.usage,
        )
        assertEquals("claude-sonnet-4-5-20250929", response.providerModel)
        assertEquals("max_tokens", response.finishReason)
    }
}
