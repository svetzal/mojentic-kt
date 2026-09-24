package com.mojentic.llm

import com.mojentic.llm.CompletionStreamEvent.Completed
import com.mojentic.llm.CompletionStreamEvent.Content
import com.mojentic.llm.CompletionStreamEvent.Error
import com.mojentic.llm.StreamErrorReason.IncompleteCompletion
import com.mojentic.llm.StreamErrorReason.IncompleteStream
import com.mojentic.llm.StreamErrorReason.RequestFailed
import com.mojentic.llm.StreamErrorReason.StreamEventsUnsupported
import com.mojentic.llm.tools.LlmTool
import com.mojentic.tracer.LlmCallEvent
import com.mojentic.tracer.LlmResponseEvent
import com.mojentic.tracer.TracerSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private open class PlainGateway : LlmGateway {
    var requests: Int = 0

    override suspend fun complete(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
    ): LlmGatewayResponse {
        requests += 1
        return LlmGatewayResponse()
    }

    override suspend fun completeJson(
        model: String,
        messages: List<LlmMessage>,
        schema: JsonObject,
        config: CompletionConfig,
    ): JsonObject {
        requests += 1
        return JsonObject(emptyMap())
    }

    override fun stream(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
    ): Flow<GatewayStreamEvent> {
        requests += 1
        return emptyFlow()
    }

    override suspend fun availableModels(): List<String> = emptyList()
}

private class ScriptedStreamGateway(private val script: Flow<CompletionStreamEvent>) :
    PlainGateway(),
    StreamEventsGateway {
    val configs = mutableListOf<CompletionConfig>()

    override fun streamEvents(
        model: String,
        messages: List<LlmMessage>,
        config: CompletionConfig,
    ): Flow<CompletionStreamEvent> {
        configs += config
        return script
    }
}

class GenerateStreamEventsTest {
    private val usage = buildJsonObject { put("total_tokens", 12) }
    private val stopped = CompletionEvidence(finishReason = "stop", usage = usage, providerModel = "reported")
    private val messages = listOf(LlmMessage.user("hi"))

    private suspend fun TracerSystem.responses(): List<LlmResponseEvent> =
        eventStore.getEvents(LlmResponseEvent::class).map { it as LlmResponseEvent }

    @Test
    fun passesContentThenCompletion() = runTest {
        val gateway = ScriptedStreamGateway(
            flow {
                emit(Content("par"))
                emit(Content("is"))
                emit(Completed(stopped))
            },
        )

        val events = LlmBroker(gateway).generateStreamEvents("configured", messages).toList()

        assertEquals(listOf(Content("par"), Content("is"), Completed(stopped)), events)
    }

    @Test
    fun forcesZeroToolIterations() = runTest {
        val gateway = ScriptedStreamGateway(flow { emit(Completed(stopped)) })

        LlmBroker(gateway).generateStreamEvents("configured", messages, CompletionConfig(maxToolIterations = null)).toList()

        assertEquals(0, gateway.configs.single().maxToolIterations)
    }

    @Test
    fun nothingFollowsTheTerminalEvent() = runTest {
        val gateway = ScriptedStreamGateway(
            flow {
                emit(Completed(stopped))
                emit(Content("late"))
            },
        )

        val events = LlmBroker(gateway).generateStreamEvents("configured", messages).toList()

        assertEquals(listOf<CompletionStreamEvent>(Completed(stopped)), events)
    }

    @Test
    fun gatewayEndingWithoutTerminalIsIncompleteStream() = runTest {
        val gateway = ScriptedStreamGateway(flow { emit(Content("par")) })

        val events = LlmBroker(gateway).generateStreamEvents("configured", messages).toList()

        assertEquals(listOf(Content("par"), Error(IncompleteStream(null))), events)
    }

    @Test
    fun gatewayFailureIsRequestFailed() = runTest {
        val failure = IllegalStateException("socket closed")
        val gateway = ScriptedStreamGateway(
            flow {
                emit(Content("par"))
                throw failure
            },
        )

        val events = LlmBroker(gateway).generateStreamEvents("configured", messages).toList()

        assertEquals(Content("par"), events.first())
        val terminal = assertIs<Error>(events.last())
        assertEquals(RequestFailed(failure), terminal.reason)
    }

    @Test
    fun unsupportedGatewayYieldsOneErrorWithoutRequestOrTrace() = runTest {
        val gateway = PlainGateway()
        val tracer = TracerSystem()

        val events = LlmBroker(gateway, tracer).generateStreamEvents("configured", messages).toList()

        assertEquals(listOf<CompletionStreamEvent>(Error(StreamEventsUnsupported)), events)
        assertEquals(0, gateway.requests)
        assertEquals(0, tracer.eventStore.size())
    }

    @Test
    fun tracesCallAndResponseWithEvidence() = runTest {
        val metadata = buildJsonObject { put("total_duration", 99) }
        val evidence = stopped.copy(metadata = metadata)
        val gateway = ScriptedStreamGateway(
            flow {
                emit(Content("par"))
                emit(Content("is"))
                emit(Completed(evidence))
            },
        )
        val tracer = TracerSystem()

        LlmBroker(gateway, tracer).generateStreamEvents("configured", messages, correlationId = "c-1").toList()

        val call = tracer.eventStore.getEvents(LlmCallEvent::class).single() as LlmCallEvent
        val response = tracer.responses().single()
        assertEquals("c-1", call.correlationId)
        assertNull(call.tools)
        assertEquals("c-1", response.correlationId)
        assertEquals("configured", response.model)
        assertEquals("paris", response.content)
        assertEquals(usage, response.usage)
        assertEquals("reported", response.providerModel)
        assertEquals("stop", response.finishReason)
        assertEquals(metadata, response.metadata)
    }

    @Test
    fun tracesIncompleteCompletionEvidence() = runTest {
        val evidence = CompletionEvidence(finishReason = "length", usage = usage)
        val gateway = ScriptedStreamGateway(
            flow {
                emit(Content("par"))
                emit(Error(IncompleteCompletion(evidence)))
            },
        )
        val tracer = TracerSystem()

        LlmBroker(gateway, tracer).generateStreamEvents("configured", messages).toList()

        val response = tracer.responses().single()
        assertEquals("par", response.content)
        assertEquals("length", response.finishReason)
        assertEquals(usage, response.usage)
    }

    @Test
    fun earlyStopTracesTheCallButNoResponse() = runTest {
        val gateway = ScriptedStreamGateway(
            flow {
                emit(Content("par"))
                emit(Completed(stopped))
            },
        )
        val tracer = TracerSystem()

        val first = LlmBroker(gateway, tracer).generateStreamEvents("configured", messages).first()

        assertEquals(Content("par"), first)
        assertEquals(1, tracer.eventStore.getEvents(LlmCallEvent::class).size)
        assertTrue(tracer.responses().isEmpty())
    }
}
