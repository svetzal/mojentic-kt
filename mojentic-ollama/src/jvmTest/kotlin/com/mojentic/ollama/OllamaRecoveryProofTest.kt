package com.mojentic.ollama

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryPolicy
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OllamaRecoveryProofTest {
    @Test
    fun truncatedPermanentResponseCannotBecomeTransportRetry(): Unit = runBlocking {
        val wires = AtomicInteger()
        val observed = CountDownLatch(1)
        val server = ServerSocket(0)
        val worker = serve(server, observed, wires)
        val gateway = OllamaGateway("http://127.0.0.1:${server.localPort}")
        try {
            val failure = assertFailsWith<RecoveryException> {
                gateway.complete(
                    "test",
                    listOf(LlmMessage.user("private-request")),
                    config = CompletionConfig(
                        recovery = RecoveryPolicy(maxAttempts = 2, admission = { true }, jitter = { 0 }, capture = {
                            if (it.inspectResponse()?.isNotEmpty() ==
                                true
                            ) {
                                observed.countDown()
                            }
                        }),
                    ),
                )
            }
            assertEquals(1, wires.get())
            assertEquals(400, failure.failures.single().status)
            assertEquals("secret-partial", failure.failures.single().inspectBytes().decodeToString())
            assertTrue(failure.failures.single().inspectCause() is java.io.IOException)
            assertTrue("secret" !in failure.toString())
        } finally {
            gateway.close()
            server.close()
            worker.join(1000)
        }
    }

    private fun serve(server: ServerSocket, observed: CountDownLatch, wires: AtomicInteger): Thread =
        thread(isDaemon = true) {
            try {
                while (!server.isClosed) {
                    server.accept().use { socket ->
                        val input = socket.getInputStream().bufferedReader()
                        val headers = generateSequence { input.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                        val length = headers.first { it.startsWith("Content-Length:", true) }.substringAfter(':').trim().toInt()
                        repeat(length) { input.read() }
                        val number = wires.incrementAndGet()
                        val body = if (number == 1) "secret-partial" else """{"message":{"role":"assistant","content":"ok"},"done":true}"""
                        val status = if (number == 1) "400 Bad Request" else "200 OK"
                        val count = if (number == 1) body.length + 20 else body.length
                        socket.getOutputStream().write(
                            "HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: $count\r\nConnection: close\r\n\r\n$body"
                                .encodeToByteArray(),
                        )
                        socket.getOutputStream().flush()
                        if (number == 1) check(observed.await(5, TimeUnit.SECONDS))
                    }
                }
            } catch (_: java.net.SocketException) {
                // Closing the owned scripted server ends its accept loop.
            }
        }
}
