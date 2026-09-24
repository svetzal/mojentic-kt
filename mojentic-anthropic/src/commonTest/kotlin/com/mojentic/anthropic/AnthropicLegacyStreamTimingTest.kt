package com.mojentic.anthropic

import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmMessage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Regression guard: the legacy [AnthropicGateway.stream] must hand each chunk to
 * the collector as it arrives, not after the whole response body is read.
 */
class AnthropicLegacyStreamTimingTest {
    private lateinit var gateway: AnthropicGateway

    @AfterTest
    fun tearDown() {
        if (::gateway.isInitialized) gateway.close()
    }

    private fun frame(content: String): String =
        "event: content_block_delta\n" +
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"$content"}}""" +
            "\n\n"

    @Test
    fun firstChunkReachesCollectorBeforeServerFinishes() = runTest {
        val body = ByteChannel(autoFlush = true)
        val firstChunkSeen = CompletableDeferred<Unit>()
        gateway = AnthropicGateway(
            apiKey = "test",
            engine = MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream")) },
        )
        val server = async(Dispatchers.Default) {
            body.writeStringUtf8(frame("Par"))
            // Hold the response open until the collector sees the first chunk, or give up.
            val seenBeforeFinish = withTimeoutOrNull(5.seconds) { firstChunkSeen.await() } != null
            body.writeStringUtf8(frame("is") + "event: message_stop\n" + """data: {"type":"message_stop"}""" + "\n\n")
            body.close()
            seenBeforeFinish
        }

        val events = withContext(Dispatchers.Default) {
            gateway.stream("claude-sonnet-4-5", listOf(LlmMessage.user("hi")))
                .onEach { firstChunkSeen.complete(Unit) }
                .toList()
        }

        assertTrue(server.await(), "first chunk must reach the collector while the server is still sending")
        assertEquals(listOf(GatewayStreamEvent.Content("Par"), GatewayStreamEvent.Content("is")), events)
    }
}
