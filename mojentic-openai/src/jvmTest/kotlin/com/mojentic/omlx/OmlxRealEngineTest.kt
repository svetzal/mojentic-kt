package com.mojentic.omlx

import com.mojentic.llm.CompletionEvidence
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Drives [OmlxGateway] through the real JVM HTTP engine (OkHttp) against a
 * local JDK HTTP server, not a `MockEngine`.
 *
 * `MockEngine` bypasses the real engine and its body handling, so it can hide
 * a mismatch between what a test fake returns and what a real client hands
 * back. The server answers with the recorded oMLX fixtures and the headers a
 * real server sends. It is not the live oMLX server.
 */
class OmlxRealEngineTest {
    private lateinit var server: HttpServer
    private lateinit var gateway: OmlxGateway
    private val authorizations = Collections.synchronizedList(mutableListOf<String?>())
    private val model = "Qwen3.8-27B-MLX-8bit"

    @BeforeTest
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/") { exchange ->
            authorizations += exchange.requestHeaders.getFirst("Authorization")
            val request = exchange.requestBody.readAllBytes().decodeToString()
            when (exchange.requestURI.path) {
                "/v1/models" -> exchange.reply("application/json", OmlxFixtures.MODELS)

                "/v1/models/$model/load" -> exchange.reply("application/json", OmlxFixtures.MODEL_LOAD)

                "/v1/chat/completions" -> if ("\"stream\":true" in request) {
                    exchange.streamSse(OmlxFixtures.STREAM_THINKING_SSE)
                } else {
                    exchange.reply("application/json", OmlxFixtures.CHAT_THINKING)
                }

                else -> exchange.reply("application/json", OmlxFixtures.ERROR_MODEL_NOT_FOUND, status = 404)
            }
        }
        server.start()
        gateway = OmlxGateway(host = "http://127.0.0.1:${server.address.port}", apiKey = "local-key", timeout = 10.seconds)
    }

    @AfterTest
    fun stopServer() {
        gateway.close()
        server.stop(0)
    }

    private fun HttpExchange.reply(contentType: String, body: String, status: Int = 200) {
        val bytes = body.encodeToByteArray()
        responseHeaders.add("Content-Type", contentType)
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    /** Sends the SSE body one frame at a time with chunked transfer encoding. */
    private fun HttpExchange.streamSse(body: String) {
        responseHeaders.add("Content-Type", "text/event-stream")
        sendResponseHeaders(200, 0)
        responseBody.use { out ->
            body.split("\n\n").filter { it.isNotEmpty() }.forEach { frame ->
                out.write("$frame\n\n".encodeToByteArray())
                out.flush()
            }
        }
    }

    @Test
    fun modelsListIsParsedFromARealJsonResponse(): Unit = runBlocking {
        val models = gateway.availableModels()

        assertEquals(listOf(model), models)
        assertEquals(listOf<String?>("Bearer local-key"), authorizations)
    }

    @Test
    fun chatCompletionIsParsedFromARealJsonResponse(): Unit = runBlocking {
        val response = gateway.complete(model, listOf(LlmMessage.user("Reply with exactly: hello")))

        assertEquals("hello", response.content)
        assertEquals("stop", response.finishReason)
        assertEquals(model, response.providerModel)
    }

    @Test
    fun loadModelSucceedsAgainstARealResponse(): Unit = runBlocking {
        gateway.loadModel(model)

        assertEquals(1, authorizations.size)
    }

    @Test
    fun streamEventsCompleteOverARealChunkedResponse(): Unit = runBlocking {
        val events = LlmBroker(gateway).generateStreamEvents(model, listOf(LlmMessage.user("Reply with exactly: hello"))).toList()

        assertEquals(CompletionStreamEvent.Content("\n\nhello"), events.first())
        val completed = events.last() as CompletionStreamEvent.Completed
        assertEquals(CompletionEvidence(finishReason = "stop", usage = completed.evidence.usage, providerModel = model), completed.evidence)
        assertEquals(2, events.size)
    }
}
