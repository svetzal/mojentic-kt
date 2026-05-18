package com.mojentic.anthropic

import app.cash.turbine.test
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmMessage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnthropicStreamingTest {
    private lateinit var gateway: AnthropicGateway

    @AfterTest
    fun tearDown() {
        if (::gateway.isInitialized) gateway.close()
    }

    private fun streamingEngine(body: String): MockEngine = MockEngine {
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
        )
    }

    @Test
    fun streamEmitsTextChunksAndCompletes() = runTest {
        val sse = listOf(
            """event: message_start""",
            """data: {"type":"message_start","message":{"id":"m_1","content":[]}}""",
            """""",
            """event: content_block_start""",
            """data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
            """""",
            """event: content_block_delta""",
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"hel"}}""",
            """""",
            """event: content_block_delta""",
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"lo"}}""",
            """""",
            """event: content_block_stop""",
            """data: {"type":"content_block_stop","index":0}""",
            """""",
            """event: message_stop""",
            """data: {"type":"message_stop"}""",
            """""",
        ).joinToString("\n") + "\n"

        gateway = AnthropicGateway(apiKey = "test", engine = streamingEngine(sse))

        gateway.stream(
            model = "claude-3-5-sonnet-latest",
            messages = listOf(LlmMessage.user("greet")),
        ).test {
            val first = awaitItem()
            val second = awaitItem()
            assertEquals("hel", (first as GatewayStreamEvent.Content).text)
            assertEquals("lo", (second as GatewayStreamEvent.Content).text)
            awaitComplete()
        }
    }

    @Test
    fun streamAccumulatesToolUseInputJsonAcrossDeltas() = runTest {
        val sse = listOf(
            """data: {"type":"message_start","message":{"id":"m_2","content":[]}}""",
            """""",
            """data: {"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"tu_1","name":"do","input":{}}}""",
            """""",
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"a\":"}}""",
            """""",
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"\"b\"}"}}""",
            """""",
            """data: {"type":"content_block_stop","index":0}""",
            """""",
            """data: {"type":"message_stop"}""",
            """""",
        ).joinToString("\n") + "\n"

        gateway = AnthropicGateway(apiKey = "test", engine = streamingEngine(sse))

        gateway.stream(
            model = "claude-3-5-sonnet-latest",
            messages = listOf(LlmMessage.user("do something")),
        ).test {
            val event = awaitItem()
            assertTrue(event is GatewayStreamEvent.ToolCalls, "expected ToolCalls, got $event")
            val call = event.calls.single()
            assertEquals("do", call.name)
            assertEquals("tu_1", call.id)
            assertEquals("b", (call.arguments["a"] as JsonPrimitive).content)
            awaitComplete()
        }
    }

    @Test
    fun streamEmitsThinkingDeltasSeparately() = runTest {
        val sse = listOf(
            """data: {"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}""",
            """""",
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"step 1"}}""",
            """""",
            """data: {"type":"content_block_stop","index":0}""",
            """""",
            """data: {"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}""",
            """""",
            """data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"final"}}""",
            """""",
            """data: {"type":"content_block_stop","index":1}""",
            """""",
            """data: {"type":"message_stop"}""",
            """""",
        ).joinToString("\n") + "\n"

        gateway = AnthropicGateway(apiKey = "test", engine = streamingEngine(sse))

        gateway.stream(
            model = "claude-3-5-sonnet-latest",
            messages = listOf(LlmMessage.user("think")),
        ).test {
            assertEquals("step 1", (awaitItem() as GatewayStreamEvent.Thinking).text)
            assertEquals("final", (awaitItem() as GatewayStreamEvent.Content).text)
            awaitComplete()
        }
    }
}
