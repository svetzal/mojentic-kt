package com.mojentic.omlx

import com.mojentic.llm.CompletionEvidence
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.CompletionStreamEvent.Completed
import com.mojentic.llm.CompletionStreamEvent.Content
import com.mojentic.llm.CompletionStreamEvent.Error
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.StreamErrorReason
import com.mojentic.omlx.OmlxTestServer.Companion.SSE_HEADERS
import com.mojentic.tracer.LlmResponseEvent
import com.mojentic.tracer.TracerSystem
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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

class OmlxStreamEventsTest {
    private var gateway: OmlxGateway? = null
    private val messages = listOf(LlmMessage.user("Reply with exactly: hello"))
    private val model = "Qwen3.8-27B-MLX-8bit"

    @AfterTest
    fun tearDown() {
        gateway?.close()
    }

    private fun OmlxTestServer.broker(tracer: TracerSystem = TracerSystem()): LlmBroker =
        LlmBroker(gateway().also { gateway = it }, tracer)

    private suspend fun events(body: String): List<CompletionStreamEvent> =
        OmlxTestServer.answering(body, headers = SSE_HEADERS).broker().generateStreamEvents(model, messages).toList()

    /** The usage object of the fixture's usage-only frame, as reported. */
    private fun usageOf(sse: String): JsonObject = sse.lineSequence()
        .filter { it.startsWith("data: {") && "\"usage\"" in it }
        .map { Json.parseToJsonElement(it.removePrefix("data: ")).jsonObject.getValue("usage").jsonObject }
        .single()

    private val keepAliveLine: String = OmlxFixtures.STREAM_THINKING_SSE.lineSequence().first()

    @Test
    fun thinkingStreamYieldsOnlyContentThenCompletedWithTheRealModel() = runTest {
        val events = events(OmlxFixtures.STREAM_THINKING_SSE)

        assertEquals(
            listOf(
                Content("\n\nhello"),
                Completed(
                    CompletionEvidence(
                        finishReason = "stop",
                        usage = usageOf(OmlxFixtures.STREAM_THINKING_SSE),
                        providerModel = model,
                    ),
                ),
            ),
            events,
        )
    }

    @Test
    fun lengthStreamIsIncompleteCompletionWithTheRealModel() = runTest {
        val events = events(OmlxFixtures.STREAM_LENGTH_SSE)

        assertEquals(
            listOf<CompletionStreamEvent>(
                Error(
                    StreamErrorReason.IncompleteCompletion(
                        CompletionEvidence(
                            finishReason = "length",
                            usage = usageOf(OmlxFixtures.STREAM_LENGTH_SSE),
                            providerModel = model,
                        ),
                    ),
                ),
            ),
            events,
        )
    }

    @Test
    fun toolCallStreamYieldsItsContentThenUnexpectedToolCalls() = runTest {
        val events = events(OmlxFixtures.STREAM_TOOL_CALL_SSE)

        assertEquals(listOf(Content("\n\n"), Error(StreamErrorReason.UnexpectedToolCalls)), events)
    }

    @Test
    fun streamOfOnlyAKeepAliveFrameIsIncompleteStreamWithNoProviderModel() = runTest {
        val events = events("$keepAliveLine\n\n")

        assertEquals(listOf<CompletionStreamEvent>(Error(StreamErrorReason.IncompleteStream(null))), events)
    }

    @Test
    fun keepAliveFrameLateInTheStreamDoesNotReplaceTheProviderModel() = runTest {
        val body = OmlxFixtures.STREAM_THINKING_SSE.replace("data: [DONE]", "$keepAliveLine\n\ndata: [DONE]")

        val completed = assertIs<Completed>(events(body).last())

        assertEquals(model, completed.evidence.providerModel)
    }

    @Test
    fun keepAliveFrameSplitAcrossBodyChunksIsStillDropped() = runTest {
        val body = ByteChannel(autoFlush = true)
        val server = OmlxTestServer { respond(body, HttpStatusCode.OK, SSE_HEADERS) }
        val writer = launch(Dispatchers.Default) {
            val half = keepAliveLine.length / 2
            body.writeStringUtf8(keepAliveLine.substring(0, half))
            body.writeStringUtf8(keepAliveLine.substring(half) + "\n\n")
            body.close()
        }

        val events = withContext(Dispatchers.Default) { server.broker().generateStreamEvents(model, messages).toList() }
        writer.join()

        assertEquals(listOf<CompletionStreamEvent>(Error(StreamErrorReason.IncompleteStream(null))), events)
    }

    @Test
    fun requestAsksForUsageAndSendsNoTools() = runTest {
        val server = OmlxTestServer.answering(OmlxFixtures.STREAM_THINKING_SSE, headers = SSE_HEADERS)

        server.broker().generateStreamEvents(model, messages).toList()

        val request = server.requests.single()
        assertEquals("http://localhost:8000/v1/chat/completions", request.url.toString())
        assertEquals(JsonPrimitive(true), server.body["stream"])
        assertEquals(buildJsonObject { put("include_usage", true) }, server.body["stream_options"])
        assertFalse("tools" in server.body, "no tools may be sent: ${server.body}")
    }

    @Test
    fun unknownModelIsProviderErrorWithStatusAndBody() = runTest {
        val events = OmlxTestServer.answering(OmlxFixtures.ERROR_MODEL_NOT_FOUND, HttpStatusCode.NotFound).broker()
            .generateStreamEvents("nope", messages)
            .toList()

        assertEquals(
            listOf<CompletionStreamEvent>(Error(StreamErrorReason.ProviderError(OmlxFixtures.ERROR_MODEL_NOT_FOUND, 404))),
            events,
        )
    }

    @Test
    fun tracedResponseCarriesTheRealModelNotKeepalive() = runTest {
        val tracer = TracerSystem()

        OmlxTestServer.answering(OmlxFixtures.STREAM_THINKING_SSE, headers = SSE_HEADERS).broker(tracer)
            .generateStreamEvents(model, messages)
            .toList()

        val response = tracer.eventStore.getEvents(LlmResponseEvent::class).single() as LlmResponseEvent
        assertEquals(model, response.providerModel)
        assertEquals(usageOf(OmlxFixtures.STREAM_THINKING_SSE), response.usage)
    }
}
