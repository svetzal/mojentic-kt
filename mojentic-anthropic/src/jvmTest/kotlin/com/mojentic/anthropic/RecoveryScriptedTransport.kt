package com.mojentic.anthropic

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.selects.select
import kotlinx.io.Buffer
import kotlinx.io.Source
import java.io.IOException

internal data class ScriptedReply(
    val status: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
    val truncated: Boolean = false,
    val release: CompletableDeferred<Unit>? = null,
    val decodedEntity: Boolean = false,
    val clientClosed: CompletableDeferred<Unit>? = null,
    val rawBody: ByteArray? = null,
    val fragmented: Boolean = false,
    val beforeReply: () -> Unit = {},
    val failure: Throwable? = null,
)

/** In-process HTTP boundary; scripts preserve request bytes and explicit body failure/cancellation. */
internal class RecoveryScriptedTransport(
    private val replies: List<ScriptedReply>,
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
) : AutoCloseable {
    val url = "https://provider.test"
    val requestLines = mutableListOf<String>()
    val requestHeaders = mutableListOf<List<String>>()
    val requests = mutableListOf<ByteArray>()
    val errors = mutableListOf<Throwable>()
    val engine = MockEngine(
        MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { request ->
                requestLines += "${request.method.value} ${request.url.encodedPath} HTTP/1.1"
                val headers = request.headers.entries().associate { it.key.lowercase() to it.value.joinToString(", ") }.toMutableMap()
                request.body.contentType?.let { headers["content-type"] = it.toString() }
                requestHeaders += headers.map { (name, value) -> "$name: $value" }
                requests += when (val body = request.body) {
                    is OutgoingContent.ByteArrayContent -> body.bytes()
                    is OutgoingContent.NoContent -> byteArrayOf()
                    else -> error("Unsupported scripted request body: ${body::class}")
                }
                val reply = replies.getOrElse(requests.lastIndex) {
                    errors += IOException("Unexpected scripted request ${requests.size}")
                    ScriptedReply(500, "unexpected-request")
                }
                reply.beforeReply()
                reply.failure?.let { throw it }
                if (reply.status == 0) throw IOException("scripted connection failure")
                // The transport supplies decoded entity bytes, as an HTTP engine does after content decoding.
                val bytes = reply.rawBody ?: reply.body.encodeToByteArray()
                val body = ScriptedBody(bytes, reply)
                val response = respond(
                    body,
                    HttpStatusCode.fromValue(reply.status),
                    Headers.build {
                        append("content-type", "application/json")
                        if (!reply.decodedEntity) {
                            append("content-length", (bytes.size + if (reply.truncated) 20 else 0).toString())
                            append("connection", "close")
                        }
                        reply.headers.forEach { (name, value) -> append(name.lowercase(), value) }
                    },
                )
                response.callContext[Job]?.invokeOnCompletion { cause -> body.cancel(cause) }
                response
            }
        },
    )

    override fun close() {
        engine.close()
    }
}

/** Finite bytes followed by clean EOF, failed EOF, or a cancellable open body; no polling or worker thread. */
@OptIn(InternalAPI::class)
private class ScriptedBody(private val bytes: ByteArray, private val reply: ScriptedReply) : ByteReadChannel {
    private val buffer = Buffer()
    private var offset = 0
    private var cancelled = false
    private val closed = CompletableDeferred<Unit>()
    private val failure = IOException("scripted truncated response")
    private val exhausted: Boolean get() = offset == bytes.size && buffer.exhausted()
    override val closedCause: Throwable? get() = if (exhausted && reply.truncated && reply.release?.isCompleted != false) failure else null
    override val isClosedForRead: Boolean
        get() = cancelled || (exhausted && reply.clientClosed == null && reply.release?.isCompleted != false)
    override val readBuffer: Source get() = buffer

    override suspend fun awaitContent(min: Int): Boolean {
        if (cancelled) return false
        if (!buffer.exhausted()) return true
        if (offset < bytes.size) {
            val count = if (reply.fragmented) 1 else bytes.size - offset
            buffer.write(bytes, offset, offset + count)
            offset += count
            return true
        }
        reply.release?.let { release ->
            select<Unit> {
                release.onAwait {}
                closed.onAwait {}
            }
        }
        if (reply.clientClosed != null) closed.await()
        return false
    }

    override fun cancel(cause: Throwable?) {
        cancelled = true
        closed.complete(Unit)
        reply.clientClosed?.complete(Unit)
    }
}
