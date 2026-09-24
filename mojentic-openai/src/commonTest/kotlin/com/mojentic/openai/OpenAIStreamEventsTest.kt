package com.mojentic.openai

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.CompletionEvidence
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.CompletionStreamEvent.Completed
import com.mojentic.llm.CompletionStreamEvent.Content
import com.mojentic.llm.CompletionStreamEvent.Error
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.ResponseFormat
import com.mojentic.llm.StreamErrorReason
import com.mojentic.tracer.LlmCallEvent
import com.mojentic.tracer.LlmResponseEvent
import com.mojentic.tracer.TracerSystem
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class OpenAIStreamEventsTest {
    private lateinit var gateway: OpenAIGateway
    private val bodies = mutableListOf<JsonObject>()
    private val messages = listOf(LlmMessage.user("capital of France?"))
    private val usage = buildJsonObject {
        put("prompt_tokens", 9)
        put("completion_tokens", 3)
        put("total_tokens", 12)
    }

    @AfterTest
    fun tearDown() {
        if (::gateway.isInitialized) gateway.close()
    }

    private fun sse(vararg frames: String): String = frames.joinToString("") { "data: $it\n\n" }

    private fun delta(content: String? = null, finish: String? = null): String {
        val contentJson = content?.let { "\"content\":${JsonPrimitive(it)}" } ?: ""
        val finishJson = finish?.let { "\"$it\"" } ?: "null"
        return """{"model":"gpt-4o-2024-08-06","choices":[{"index":0,"delta":{$contentJson},"finish_reason":$finishJson}]}"""
    }

    private val usageFrame get() = """{"model":"gpt-4o-2024-08-06","choices":[],"usage":$usage}"""

    private fun broker(body: String, status: HttpStatusCode = HttpStatusCode.OK, tracer: TracerSystem = TracerSystem()): LlmBroker {
        gateway = OpenAIGateway(
            apiKey = "test",
            engine = MockEngine { request ->
                bodies += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                respond(body, status, headersOf(HttpHeaders.ContentType, "text/event-stream"))
            },
        )
        return LlmBroker(gateway, tracer)
    }

    @Test
    fun contentThenStopThenDoneYieldsContentThenCompleted() = runTest {
        val events = broker(sse(delta("Par"), delta("is"), delta(finish = "stop"), usageFrame, "[DONE]"))
            .generateStreamEvents("gpt-4o", messages)
            .toList()

        assertEquals(
            listOf(
                Content("Par"),
                Content("is"),
                Completed(CompletionEvidence(finishReason = "stop", usage = usage, providerModel = "gpt-4o-2024-08-06")),
            ),
            events,
        )
    }

    @Test
    fun requestAsksForUsageForwardsFormatAndSendsNoTools() = runTest {
        val schema = buildJsonObject { put("type", "object") }
        broker(sse(delta(finish = "stop"), "[DONE]"))
            .generateStreamEvents("gpt-4o", messages, CompletionConfig(responseFormat = ResponseFormat.Json(schema)))
            .toList()

        val body = bodies.single()
        assertEquals(JsonPrimitive(true), body["stream"])
        assertEquals(buildJsonObject { put("include_usage", true) }, body["stream_options"])
        assertEquals("json_schema", (body["response_format"] as JsonObject)["type"].let { (it as JsonPrimitive).content })
        assertFalse("tools" in body, "no tools may be sent: $body")
    }

    @Test
    fun doneWithLengthFinishIsIncompleteCompletionWithEvidence() = runTest {
        val events = broker(sse(delta("Par"), delta(finish = "length"), usageFrame, "[DONE]"))
            .generateStreamEvents("gpt-4o", messages)
            .toList()

        assertEquals(Content("Par"), events.first())
        assertEquals(
            Error(
                StreamErrorReason.IncompleteCompletion(
                    CompletionEvidence(finishReason = "length", usage = usage, providerModel = "gpt-4o-2024-08-06"),
                ),
            ),
            events.last(),
        )
    }

    @Test
    fun stopWithoutDoneIsIncompleteStreamWithPartialEvidence() = runTest {
        val events = broker(sse(delta("Par"), delta(finish = "stop")))
            .generateStreamEvents("gpt-4o", messages)
            .toList()

        assertEquals(
            Error(
                StreamErrorReason.IncompleteStream(
                    CompletionEvidence(finishReason = "stop", providerModel = "gpt-4o-2024-08-06"),
                ),
            ),
            events.last(),
        )
    }

    @Test
    fun emptyStreamIsIncompleteStreamWithoutEvidence() = runTest {
        val events = broker("").generateStreamEvents("gpt-4o", messages).toList()

        assertEquals(listOf<CompletionStreamEvent>(Error(StreamErrorReason.IncompleteStream(null))), events)
    }

    @Test
    fun toolCallDeltaIsUnexpectedToolCalls() = runTest {
        val toolDelta = """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"c1","type":"function",""" +
            """"function":{"name":"lookup","arguments":""}}]},"finish_reason":null}]}"""

        val events = broker(sse(delta("Par"), toolDelta, delta(finish = "stop"), "[DONE]"))
            .generateStreamEvents("gpt-4o", messages)
            .toList()

        assertEquals(listOf(Content("Par"), Error(StreamErrorReason.UnexpectedToolCalls)), events)
    }

    @Test
    fun providerErrorFrameIsProviderError() = runTest {
        val events = broker(sse("""{"error":{"message":"overloaded","type":"server_error"}}"""))
            .generateStreamEvents("gpt-4o", messages)
            .toList()

        val error = assertIs<Error>(events.single())
        val reason = assertIs<StreamErrorReason.ProviderError>(error.reason)
        assertTrue("overloaded" in reason.detail)
    }

    @Test
    fun nonSuccessStatusIsProviderErrorWithStatus() = runTest {
        val events = broker("""{"error":{"message":"bad key"}}""", HttpStatusCode.Unauthorized)
            .generateStreamEvents("gpt-4o", messages)
            .toList()

        val error = assertIs<Error>(events.single())
        val reason = assertIs<StreamErrorReason.ProviderError>(error.reason)
        assertEquals(401, reason.status)
        assertTrue("bad key" in reason.detail)
    }

    @Test
    fun malformedFrameIsInvalidStreamEvent() = runTest {
        val events = broker(sse("{not json", "[DONE]")).generateStreamEvents("gpt-4o", messages).toList()

        assertEquals(listOf<CompletionStreamEvent>(Error(StreamErrorReason.InvalidStreamEvent("{not json"))), events)
    }

    @Test
    fun tracerRecordsCallAndResponseWithUsage() = runTest {
        val tracer = TracerSystem()
        broker(sse(delta("Paris"), delta(finish = "stop"), usageFrame, "[DONE]"), tracer = tracer)
            .generateStreamEvents("gpt-4o", messages)
            .toList()

        assertEquals(1, tracer.eventStore.getEvents(LlmCallEvent::class).size)
        val response = tracer.eventStore.getEvents(LlmResponseEvent::class).single() as LlmResponseEvent
        assertEquals("gpt-4o", response.model)
        assertEquals("Paris", response.content)
        assertEquals(usage, response.usage)
        assertEquals("gpt-4o-2024-08-06", response.providerModel)
        assertEquals("stop", response.finishReason)
    }

    @Test
    fun stoppingConsumptionEarlyCancelsTheRequest() = runTest {
        val body = ByteChannel(autoFlush = true)
        gateway = OpenAIGateway(
            apiKey = "test",
            engine = MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream")) },
        )
        val writer = launch(Dispatchers.Default) {
            // Writes fail once the gateway cancels the response body, which ends the loop.
            runCatching {
                body.writeStringUtf8(sse(delta("Par")))
                while (true) {
                    delay(10)
                    body.writeStringUtf8(": keep-alive\n")
                }
            }
        }

        val first = LlmBroker(gateway).generateStreamEvents("gpt-4o", messages).first()

        assertEquals(Content("Par"), first)
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) { writer.join() }
        }
        assertTrue(body.isClosedForWrite, "the response body must be cancelled after the consumer stops")
    }
}
