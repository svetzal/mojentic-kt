package com.mojentic.ollama

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

class OllamaStreamEventsTest {
    private lateinit var gateway: OllamaGateway
    private val bodies = mutableListOf<JsonObject>()
    private val messages = listOf(LlmMessage.user("capital of France?"))

    private val usage = buildJsonObject {
        put("prompt_eval_count", 26)
        put("eval_count", 290)
    }
    private val timings = buildJsonObject {
        put("total_duration", 5000)
        put("eval_duration", 4000)
    }

    @AfterTest
    fun tearDown() {
        if (::gateway.isInitialized) gateway.close()
    }

    private fun lines(vararg frames: String): String = frames.joinToString("") { "$it\n" }

    private fun content(text: String): String =
        """{"model":"qwen3:32b","message":{"role":"assistant","content":${JsonPrimitive(text)}},"done":false}"""

    private fun done(reason: String?, withEvidence: Boolean = true): String {
        val reasonJson = reason?.let { ""","done_reason":"$it"""" } ?: ""
        val evidence = if (withEvidence) {
            ""","prompt_eval_count":26,"eval_count":290,"total_duration":5000,"eval_duration":4000"""
        } else {
            ""
        }
        return """{"model":"qwen3:32b","message":{"role":"assistant","content":""},"done":true$reasonJson$evidence}"""
    }

    private fun broker(body: String, status: HttpStatusCode = HttpStatusCode.OK, tracer: TracerSystem = TracerSystem()): LlmBroker {
        gateway = OllamaGateway(
            engine = MockEngine { request ->
                bodies += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/x-ndjson"))
            },
        )
        return LlmBroker(gateway, tracer)
    }

    private val stopEvidence = CompletionEvidence(finishReason = "stop", usage = usage, providerModel = "qwen3:32b", metadata = timings)

    @Test
    fun contentThenStopFrameYieldsContentThenCompleted() = runTest {
        val events = broker(lines(content("Par"), content("is"), done("stop")))
            .generateStreamEvents("qwen3", messages)
            .toList()

        assertEquals(listOf(Content("Par"), Content("is"), Completed(stopEvidence)), events)
    }

    @Test
    fun requestStreamsWithoutToolsAndForwardsFormat() = runTest {
        broker(lines(done("stop")))
            .generateStreamEvents("qwen3", messages, CompletionConfig(responseFormat = ResponseFormat.Json()))
            .toList()

        val body = bodies.single()
        assertEquals(JsonPrimitive(true), body["stream"])
        assertEquals(JsonPrimitive("json"), body["format"])
        assertFalse("tools" in body, "no tools may be sent: $body")
    }

    @Test
    fun lengthDoneReasonIsIncompleteCompletionWithEvidence() = runTest {
        val events = broker(lines(content("Par"), done("length")))
            .generateStreamEvents("qwen3", messages)
            .toList()

        assertEquals(Error(StreamErrorReason.IncompleteCompletion(stopEvidence.copy(finishReason = "length"))), events.last())
    }

    @Test
    fun missingDoneReasonIsIncompleteCompletion() = runTest {
        val events = broker(lines(content("Par"), done(null, withEvidence = false)))
            .generateStreamEvents("qwen3", messages)
            .toList()

        assertEquals(
            Error(StreamErrorReason.IncompleteCompletion(CompletionEvidence(providerModel = "qwen3:32b"))),
            events.last(),
        )
    }

    @Test
    fun endOfStreamWithoutDoneIsIncompleteStreamWithPartialEvidence() = runTest {
        val events = broker(lines(content("Par"))).generateStreamEvents("qwen3", messages).toList()

        assertEquals(
            listOf(Content("Par"), Error(StreamErrorReason.IncompleteStream(CompletionEvidence(providerModel = "qwen3:32b")))),
            events,
        )
    }

    @Test
    fun emptyStreamIsIncompleteStreamWithoutEvidence() = runTest {
        val events = broker("").generateStreamEvents("qwen3", messages).toList()

        assertEquals(listOf<CompletionStreamEvent>(Error(StreamErrorReason.IncompleteStream(null))), events)
    }

    @Test
    fun toolCallFrameIsUnexpectedToolCalls() = runTest {
        val toolFrame = """{"model":"qwen3:32b","message":{"role":"assistant","content":"",""" +
            """"tool_calls":[{"function":{"name":"lookup","arguments":{}}}]},"done":false}"""

        val events = broker(lines(content("Par"), toolFrame, done("stop")))
            .generateStreamEvents("qwen3", messages)
            .toList()

        assertEquals(listOf(Content("Par"), Error(StreamErrorReason.UnexpectedToolCalls)), events)
    }

    @Test
    fun providerErrorFrameIsProviderError() = runTest {
        val events = broker(lines("""{"error":"model 'qwen3' not found"}"""))
            .generateStreamEvents("qwen3", messages)
            .toList()

        val error = assertIs<Error>(events.single())
        val reason = assertIs<StreamErrorReason.ProviderError>(error.reason)
        assertTrue("not found" in reason.detail)
    }

    @Test
    fun nonSuccessStatusIsProviderErrorWithStatus() = runTest {
        val events = broker("""{"error":"model 'qwen3' not found"}""", HttpStatusCode.NotFound)
            .generateStreamEvents("qwen3", messages)
            .toList()

        val error = assertIs<Error>(events.single())
        assertEquals(404, assertIs<StreamErrorReason.ProviderError>(error.reason).status)
    }

    @Test
    fun malformedFrameIsInvalidStreamEvent() = runTest {
        val events = broker(lines("{not json", done("stop"))).generateStreamEvents("qwen3", messages).toList()

        assertEquals(listOf<CompletionStreamEvent>(Error(StreamErrorReason.InvalidStreamEvent("{not json"))), events)
    }

    @Test
    fun tracerRecordsCallAndResponseWithUsageAndMetadata() = runTest {
        val tracer = TracerSystem()
        broker(lines(content("Paris"), done("stop")), tracer = tracer).generateStreamEvents("qwen3", messages).toList()

        assertEquals(1, tracer.eventStore.getEvents(LlmCallEvent::class).size)
        val response = tracer.eventStore.getEvents(LlmResponseEvent::class).single() as LlmResponseEvent
        assertEquals("qwen3", response.model)
        assertEquals("Paris", response.content)
        assertEquals(usage, response.usage)
        assertEquals(timings, response.metadata)
        assertEquals("qwen3:32b", response.providerModel)
        assertEquals("stop", response.finishReason)
    }

    @Test
    fun stoppingConsumptionEarlyCancelsTheRequest() = runTest {
        val body = ByteChannel(autoFlush = true)
        gateway = OllamaGateway(
            engine = MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/x-ndjson")) },
        )
        val writer = launch(Dispatchers.Default) {
            // Writes fail once the gateway cancels the response body, which ends the loop.
            runCatching {
                body.writeStringUtf8(lines(content("Par")))
                while (true) {
                    delay(10)
                    body.writeStringUtf8("\n")
                }
            }
        }

        val first = LlmBroker(gateway).generateStreamEvents("qwen3", messages).first()

        assertEquals(Content("Par"), first)
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) { writer.join() }
        }
        assertTrue(body.isClosedForWrite, "the response body must be cancelled after the consumer stops")
    }
}
