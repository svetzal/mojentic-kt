package com.mojentic.ollama

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
 * Regression guard: the legacy [OllamaGateway.stream] must hand each chunk to
 * the collector as it arrives, not after the whole response body is read.
 */
class OllamaLegacyStreamTimingTest {
    private lateinit var gateway: OllamaGateway

    @AfterTest
    fun tearDown() {
        if (::gateway.isInitialized) gateway.close()
    }

    private fun frame(content: String): String =
        """{"model":"qwen3","message":{"role":"assistant","content":"$content"},"done":false}""" + "\n"

    @Test
    fun firstChunkReachesCollectorBeforeServerFinishes() = runTest {
        val body = ByteChannel(autoFlush = true)
        val firstChunkSeen = CompletableDeferred<Unit>()
        gateway = OllamaGateway(
            engine = MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/x-ndjson")) },
        )
        val server = async(Dispatchers.Default) {
            body.writeStringUtf8(frame("Par"))
            // Hold the response open until the collector sees the first chunk, or give up.
            val seenBeforeFinish = withTimeoutOrNull(5.seconds) { firstChunkSeen.await() } != null
            body.writeStringUtf8(
                frame("is") + """{"model":"qwen3","message":{"role":"assistant","content":""},"done":true}""" + "\n",
            )
            body.close()
            seenBeforeFinish
        }

        val events = withContext(Dispatchers.Default) {
            gateway.stream("qwen3", listOf(LlmMessage.user("hi")))
                .onEach { firstChunkSeen.complete(Unit) }
                .toList()
        }

        assertTrue(server.await(), "first chunk must reach the collector while the server is still sending")
        assertEquals(listOf(GatewayStreamEvent.Content("Par"), GatewayStreamEvent.Content("is")), events)
    }
}
