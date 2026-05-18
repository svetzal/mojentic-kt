package com.mojentic.llm

import app.cash.turbine.test
import com.mojentic.errors.MaxToolIterationsExceededException
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Serializable
private data class Greeting(val text: String)

private class StubGateway(
    private val completions: ArrayDeque<LlmGatewayResponse> = ArrayDeque(),
    private var structured: JsonObject = JsonObject(emptyMap()),
    private val streams: ArrayDeque<List<GatewayStreamEvent>> = ArrayDeque(),
) : LlmGateway {
    val completeCalls = mutableListOf<List<LlmMessage>>()

    fun queueComplete(response: LlmGatewayResponse) {
        completions.add(response)
    }

    fun queueStream(events: List<GatewayStreamEvent>) {
        streams.add(events)
    }

    fun setStructured(value: JsonObject) {
        structured = value
    }

    override suspend fun complete(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
    ): LlmGatewayResponse {
        completeCalls += messages
        return completions.removeFirst()
    }

    override suspend fun completeJson(
        model: String,
        messages: List<LlmMessage>,
        schema: JsonObject,
        config: CompletionConfig,
    ): JsonObject = structured

    override fun stream(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
    ): Flow<GatewayStreamEvent> = flowOf(*streams.removeFirst().toTypedArray())

    override suspend fun availableModels(): List<String> = listOf("stub-1", "stub-2")
}

private class CountingTool(
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "increment",
        description = "Adds one",
        parameters = buildJsonObject { put("type", JsonPrimitive("object")) },
    ),
) : LlmTool {
    var calls: Int = 0
        private set

    override suspend fun execute(arguments: JsonObject): String {
        calls += 1
        return """{"value":$calls}"""
    }
}

class BrokerTest {
    @Test
    fun completeReturnsAssistantTextWhenNoToolCallsRequested() = runTest {
        val gateway = StubGateway()
        gateway.queueComplete(LlmGatewayResponse(content = "hello"))
        val broker = LlmBroker(gateway)

        val response = broker.complete("any", listOf(LlmMessage.user("hi")))

        assertEquals("hello", response.content)
        assertEquals(1, gateway.completeCalls.size)
    }

    @Test
    fun completeRunsRequestedToolsAndRecurses() = runTest {
        val gateway = StubGateway()
        val tool = CountingTool()
        val call = LlmToolCall(name = "increment", arguments = JsonObject(emptyMap()))
        gateway.queueComplete(LlmGatewayResponse(toolCalls = listOf(call)))
        gateway.queueComplete(LlmGatewayResponse(content = "done"))
        val broker = LlmBroker(gateway)

        val response = broker.complete("any", listOf(LlmMessage.user("hi")), tools = listOf(tool))

        assertEquals("done", response.content)
        assertEquals(1, tool.calls)
        assertEquals(2, gateway.completeCalls.size)
        // Second call must include the assistant + tool message pair from round one.
        val secondCallMessages = gateway.completeCalls[1]
        assertEquals(3, secondCallMessages.size)
        assertEquals(MessageRole.Assistant, secondCallMessages[1].role)
        assertEquals(MessageRole.Tool, secondCallMessages[2].role)
    }

    @Test
    fun completeRaisesWhenIterationBudgetExhausted() = runTest {
        val gateway = StubGateway()
        val broker = LlmBroker(gateway)

        assertFailsWith<MaxToolIterationsExceededException> {
            broker.complete(
                "any",
                listOf(LlmMessage.user("hi")),
                config = CompletionConfig(maxToolIterations = 0),
            )
        }
    }

    @Test
    fun completeJsonRoundTripsThroughGateway() = runTest {
        val gateway = StubGateway()
        gateway.setStructured(buildJsonObject { put("text", JsonPrimitive("greetings")) })
        val broker = LlmBroker(gateway)

        val greeting: Greeting = broker.completeJson("any", listOf(LlmMessage.user("greet me")))

        assertEquals(Greeting("greetings"), greeting)
    }

    @Test
    fun streamEmitsTextChunksThenTerminates() = runTest {
        val gateway = StubGateway()
        gateway.queueStream(
            listOf(
                GatewayStreamEvent.Content("hel"),
                GatewayStreamEvent.Content("lo"),
            ),
        )
        val broker = LlmBroker(gateway)

        broker.stream("any", listOf(LlmMessage.user("hi"))).test {
            assertEquals(StreamEvent.TextChunk("hel"), awaitItem())
            assertEquals(StreamEvent.TextChunk("lo"), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun streamDispatchesToolCallsAndContinuesAfterRecursion() = runTest {
        val gateway = StubGateway()
        val tool = CountingTool()
        val call = LlmToolCall(name = "increment", arguments = JsonObject(emptyMap()))
        gateway.queueStream(listOf(GatewayStreamEvent.ToolCalls(listOf(call))))
        gateway.queueStream(listOf(GatewayStreamEvent.Content("done")))
        val broker = LlmBroker(gateway)

        broker.stream("any", listOf(LlmMessage.user("hi")), tools = listOf(tool)).test {
            assertTrue(awaitItem() is StreamEvent.ToolCall)
            val result = awaitItem() as StreamEvent.ToolResult
            assertEquals(false, result.isError)
            assertEquals(StreamEvent.TextChunk("done"), awaitItem())
            awaitComplete()
        }
        assertEquals(1, tool.calls)
    }
}
