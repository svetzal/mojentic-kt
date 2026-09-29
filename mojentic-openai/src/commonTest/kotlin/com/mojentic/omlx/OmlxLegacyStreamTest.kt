package com.mojentic.omlx

import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmMessage
import com.mojentic.omlx.OmlxTestServer.Companion.SSE_HEADERS
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class OmlxLegacyStreamTest {
    private var gateway: OmlxGateway? = null
    private val messages = listOf(LlmMessage.user("What is today's date? Use the tool."))
    private val model = "Qwen3.8-27B-MLX-8bit"

    @AfterTest
    fun tearDown() {
        gateway?.close()
    }

    private suspend fun stream(server: OmlxTestServer): List<GatewayStreamEvent> =
        server.gateway().also { gateway = it }.stream(model, messages).toList()

    private suspend fun stream(body: String): List<GatewayStreamEvent> =
        stream(OmlxTestServer.answering(body, headers = SSE_HEADERS))

    @Test
    fun toolCallStreamYieldsItsContentThenExactlyOneCompleteToolCall() = runTest {
        val events = stream(OmlxFixtures.STREAM_TOOL_CALL_SSE)

        assertEquals(listOf(GatewayStreamEvent.Content("\n\n")), events.filterIsInstance<GatewayStreamEvent.Content>())
        val toolCalls = assertIs<GatewayStreamEvent.ToolCalls>(events.last())
        assertEquals(1, events.count { it is GatewayStreamEvent.ToolCalls })
        val call = toolCalls.calls.single()
        assertEquals("call_659d0e77", call.id)
        assertEquals("resolve_date", call.name)
        assertEquals(buildJsonObject { put("relative", "today") }, call.arguments)
    }

    @Test
    fun reasoningDeltasBecomeThinkingChunks() = runTest {
        val events = stream(OmlxFixtures.STREAM_THINKING_SSE)

        val thinking = events.filterIsInstance<GatewayStreamEvent.Thinking>().joinToString("") { it.text }
        assertEquals(
            "\nWe need to reply exactly: hello. User said \"Reply with exactly: hello\". Need final \"hello\". Ensure no extra.\n",
            thinking,
        )
        assertEquals(listOf(GatewayStreamEvent.Content("\n\nhello")), events.filterIsInstance<GatewayStreamEvent.Content>())
    }

    @Test
    fun legacyRequestDoesNotAskForUsage() = runTest {
        val server = OmlxTestServer.answering(OmlxFixtures.STREAM_THINKING_SSE, headers = SSE_HEADERS)

        stream(server)

        assertEquals("http://localhost:8000/v1/chat/completions", server.requests.single().url.toString())
        assertFalse("stream_options" in server.body, "legacy streaming cannot hand usage back: ${server.body}")
    }

    @Test
    fun keepAliveFrameYieldsNothing() = runTest {
        val keepAlive = OmlxFixtures.STREAM_THINKING_SSE.lineSequence().first()

        assertEquals(emptyList(), stream("$keepAlive\n\ndata: [DONE]\n\n"))
    }

    @Test
    fun nonSuccessStatusIsAProviderError() = runTest {
        val server = OmlxTestServer.answering(OmlxFixtures.ERROR_MODEL_NOT_FOUND, HttpStatusCode.NotFound)

        val error = assertFailsWith<LlmGatewayException> { stream(server) }

        assertEquals(true, error.message?.contains(OmlxFixtures.ERROR_MODEL_NOT_FOUND))
    }
}
