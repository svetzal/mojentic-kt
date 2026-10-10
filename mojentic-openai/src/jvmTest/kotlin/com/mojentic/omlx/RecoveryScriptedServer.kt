package com.mojentic.omlx

import java.io.InputStream
import java.net.ServerSocket
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal data class ScriptedReply(
    val status: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
    val truncated: Boolean = false,
    val release: CountDownLatch? = null,
)

/** Exact HTTP/1.1 loopback scripts, including deliberately incomplete response bodies. */
internal class RecoveryScriptedServer(private val replies: List<ScriptedReply>) : AutoCloseable {
    private val server = ServerSocket(0)
    val url = "http://127.0.0.1:${server.localPort}"
    val requests: MutableList<ByteArray> = Collections.synchronizedList(mutableListOf())
    val errors: MutableList<Throwable> = Collections.synchronizedList(mutableListOf())
    private val worker = thread(isDaemon = true) {
        try {
            while (!server.isClosed) {
                server.accept().use { socket ->
                    val input = socket.getInputStream().buffered()
                    var length = 0
                    while (true) {
                        val line = input.line()
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                    }
                    requests += input.readNBytes(length)
                    val reply = replies.getOrElse(requests.size - 1) { ScriptedReply(500, "unexpected-request") }
                    if (reply.status != 0) respond(socket.getOutputStream(), reply)
                }
            }
        } catch (_: SocketException) {
            // Owned server closure ends the accept loop.
        } catch (failure: Throwable) {
            errors += failure
        }
    }

    private fun respond(output: java.io.OutputStream, reply: ScriptedReply) {
        val bytes = reply.body.encodeToByteArray()
        val count = bytes.size + if (reply.truncated) TRUNCATED_EXTRA else 0
        val headers = reply.headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" }
        val head = "HTTP/1.1 ${reply.status} Scripted\r\nContent-Type: application/json\r\n" +
            "Content-Length: $count\r\nConnection: close\r\n$headers\r\n"
        output.write(head.encodeToByteArray())
        output.write(bytes)
        output.flush()
        check(reply.release?.await(WAIT_SECONDS, TimeUnit.SECONDS) != false) { "fixture release timed out" }
    }

    override fun close() {
        replies.forEach { it.release?.countDown() }
        server.close()
        worker.join(JOIN_MILLIS)
        check(errors.isEmpty()) { "Scripted HTTP fixture failed" }
    }

    private fun InputStream.line(): String = buildString {
        while (true) {
            val byte = read()
            if (byte == -1 || byte == '\n'.code) break
            if (byte != '\r'.code) append(byte.toChar())
        }
    }

    private companion object {
        const val TRUNCATED_EXTRA = 20
        const val WAIT_SECONDS = 10L
        const val JOIN_MILLIS = 1000L
    }
}
