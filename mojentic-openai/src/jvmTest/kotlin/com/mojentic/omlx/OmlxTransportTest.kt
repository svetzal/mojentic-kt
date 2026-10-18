package com.mojentic.omlx

import com.mojentic.llm.CompletionEvidence
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.openai.providerEvidence
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** Public oMLX and broker wiring against in-process recorded provider responses. */
class OmlxTransportTest {
    private lateinit var gateway: OmlxGateway
    private val authorizations = mutableListOf<String?>()
    private val model = "Qwen3.8-27B-MLX-8bit"

    @BeforeTest
    fun startTransport() {
        val transport = OmlxTestServer { request ->
            authorizations += request.headers["Authorization"]
            val payload = (request.body as? io.ktor.http.content.TextContent)?.text.orEmpty()
            val body = when (request.url.encodedPath) {
                "/v1/models" -> OmlxFixtures.MODELS
                "/v1/models/$model/load" -> OmlxFixtures.MODEL_LOAD
                "/v1/chat/completions" -> if ("\"stream\":true" in payload) OmlxFixtures.STREAM_THINKING_SSE else OmlxFixtures.CHAT_THINKING
                else -> error("Unexpected path ${request.url.encodedPath}")
            }
            respond(body, headers = OmlxTestServer.JSON_HEADERS)
        }
        gateway = transport.gateway(apiKey = "local-key", timeout = 10.seconds)
    }

    @AfterTest
    fun stopTransport() {
        gateway.close()
    }

    @Test
    fun modelsListIsParsedFromJsonResponse(): Unit = runTest {
        val models = gateway.availableModels()

        assertEquals(listOf(model), models)
        assertEquals(listOf<String?>("Bearer local-key"), authorizations)
    }

    @Test
    fun chatCompletionIsParsedFromJsonResponse(): Unit = runTest {
        val response = gateway.complete(model, listOf(LlmMessage.user("Reply with exactly: hello")))

        assertEquals("hello", response.content)
        assertEquals("stop", response.finishReason)
        assertEquals(model, response.providerModel)
    }

    @Test
    fun loadModelSucceedsAgainstResponse(): Unit = runTest {
        gateway.loadModel(model)

        assertEquals(1, authorizations.size)
    }

    @Test
    fun streamEventsCompleteThroughTransport(): Unit = runTest {
        val events = LlmBroker(
            gateway,
        ).generateStreamEvents(model, listOf(LlmMessage.user("Reply with exactly: hello"))).toList().providerEvidence()

        assertEquals(CompletionStreamEvent.Content("\n\nhello"), events.first())
        val completed = events.last() as CompletionStreamEvent.Completed
        assertEquals(CompletionEvidence(finishReason = "stop", usage = completed.evidence.usage, providerModel = model), completed.evidence)
        assertEquals(2, events.size)
    }
}
