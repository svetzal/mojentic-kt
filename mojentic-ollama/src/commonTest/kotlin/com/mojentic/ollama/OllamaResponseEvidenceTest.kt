package com.mojentic.ollama

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
import kotlin.test.assertNull

class OllamaResponseEvidenceTest {
    private lateinit var gateway: OllamaGateway

    @AfterTest
    fun tearDown() {
        if (::gateway.isInitialized) gateway.close()
    }

    private fun newGateway(body: String): OllamaGateway = OllamaGateway(
        engine = MockEngine {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    ).also { gateway = it }

    @Test
    fun completeReportsCountsAsUsageAndDurationsAsMetadata() = runTest {
        val response = newGateway(
            """{"model":"qwen3:32b","message":{"role":"assistant","content":"hi"},"done":true,""" +
                """"done_reason":"stop","prompt_eval_count":26,"eval_count":290,""" +
                """"total_duration":5000,"load_duration":100,"prompt_eval_duration":900,"eval_duration":4000}""",
        ).complete("qwen3", listOf(LlmMessage.user("hi")))

        assertEquals(
            buildJsonObject {
                put("prompt_eval_count", 26)
                put("eval_count", 290)
            },
            response.usage,
        )
        assertEquals(
            buildJsonObject {
                put("total_duration", 5000)
                put("load_duration", 100)
                put("prompt_eval_duration", 900)
                put("eval_duration", 4000)
            },
            response.metadata,
        )
        assertEquals("qwen3:32b", response.providerModel)
        assertEquals("stop", response.finishReason)
    }

    @Test
    fun completeReportsNullEvidenceWhenProviderSendsNone() = runTest {
        val response = newGateway("""{"message":{"role":"assistant","content":"hi"},"done":true}""")
            .complete("qwen3", listOf(LlmMessage.user("hi")))

        assertNull(response.usage)
        assertNull(response.metadata)
        assertNull(response.providerModel)
        assertNull(response.finishReason)
    }

    @Test
    fun structuredResponseCarriesEvidence() = runTest {
        val response = newGateway(
            """{"model":"qwen3:32b","message":{"role":"assistant","content":"{\"a\":\"b\"}"},"done":true,""" +
                """"done_reason":"stop","eval_count":3}""",
        ).completeJsonResponse("qwen3", listOf(LlmMessage.user("hi")), buildJsonObject { put("type", "object") })

        assertEquals(buildJsonObject { put("a", "b") }, response.structuredJson)
        assertEquals(buildJsonObject { put("eval_count", 3) }, response.usage)
        assertEquals("stop", response.finishReason)
    }
}
