package com.mojentic.llm

import app.cash.turbine.test
import com.mojentic.llm.tools.LlmTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class RecordingGateway(
    private val replies: ArrayDeque<LlmGatewayResponse> = ArrayDeque(),
    private val streams: ArrayDeque<List<GatewayStreamEvent>> = ArrayDeque(),
) : LlmGateway {
    val seenMessages: MutableList<List<LlmMessage>> = mutableListOf()
    var failNext: Throwable? = null

    fun queue(response: LlmGatewayResponse) {
        replies.add(response)
    }

    fun queueStream(events: List<GatewayStreamEvent>) {
        streams.add(events)
    }

    override suspend fun complete(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
    ): LlmGatewayResponse {
        seenMessages += messages
        failNext?.let {
            failNext = null
            throw it
        }
        return replies.removeFirst()
    }

    override suspend fun completeJson(
        model: String,
        messages: List<LlmMessage>,
        schema: JsonObject,
        config: CompletionConfig,
    ): JsonObject = JsonObject(emptyMap())

    override fun stream(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
    ): Flow<GatewayStreamEvent> {
        seenMessages += messages
        return flowOf(*streams.removeFirst().toTypedArray())
    }

    override suspend fun availableModels(): List<String> = emptyList()
}

class ChatSessionTest {
    @Test
    fun sendAppendsUserAndAssistantToHistory() = runTest {
        val gateway = RecordingGateway()
        gateway.queue(LlmGatewayResponse(content = "hi there"))
        val session = ChatSession(LlmBroker(gateway), model = "stub")

        val response = session.send("hello")

        assertEquals("hi there", response.content)
        val messages = session.messages()
        assertEquals(2, messages.size)
        assertEquals(MessageRole.User, messages[0].role)
        assertEquals("hello", messages[0].content)
        assertEquals(MessageRole.Assistant, messages[1].role)
        assertEquals("hi there", messages[1].content)
    }

    @Test
    fun sendCarriesSystemPromptOnFirstTurn() = runTest {
        val gateway = RecordingGateway()
        gateway.queue(LlmGatewayResponse(content = "ok"))
        val session = ChatSession(LlmBroker(gateway), model = "stub", systemPrompt = "be terse")

        session.send("hi")

        val conversationSentToGateway = gateway.seenMessages.first()
        assertEquals(MessageRole.System, conversationSentToGateway[0].role)
        assertEquals("be terse", conversationSentToGateway[0].content)
        assertEquals(MessageRole.User, conversationSentToGateway[1].role)
    }

    @Test
    fun sendRollsBackHistoryOnFailure() = runTest {
        val gateway = RecordingGateway()
        gateway.failNext = RuntimeException("boom")
        val session = ChatSession(LlmBroker(gateway), model = "stub", systemPrompt = "system")

        assertFailsWith<RuntimeException> { session.send("hello") }

        val messages = session.messages()
        assertEquals(1, messages.size)
        assertEquals(MessageRole.System, messages[0].role)
    }

    @Test
    fun resetPreservesSystemPromptOnly() = runTest {
        val gateway = RecordingGateway()
        gateway.queue(LlmGatewayResponse(content = "first"))
        gateway.queue(LlmGatewayResponse(content = "second"))
        val session = ChatSession(LlmBroker(gateway), model = "stub", systemPrompt = "context")

        session.send("one")
        session.send("two")
        session.reset()

        val messages = session.messages()
        assertEquals(1, messages.size)
        assertEquals(MessageRole.System, messages[0].role)
    }

    @Test
    fun streamUpdatesHistoryAfterFlowCompletes() = runTest {
        val gateway = RecordingGateway()
        gateway.queueStream(
            listOf(
                GatewayStreamEvent.Content("hel"),
                GatewayStreamEvent.Content("lo"),
            ),
        )
        val session = ChatSession(LlmBroker(gateway), model = "stub")

        session.stream("hi").test {
            assertEquals(StreamEvent.TextChunk("hel"), awaitItem())
            assertEquals(StreamEvent.TextChunk("lo"), awaitItem())
            awaitComplete()
        }

        val messages = session.messages()
        assertEquals(2, messages.size)
        assertEquals("hello", messages[1].content)
        assertTrue(messages[1].role == MessageRole.Assistant)
    }
}
