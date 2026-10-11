package com.mojentic.anthropic

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.test.assertIs

/** Observes the real transport channel without replacing bytes, failures or HTTP execution. */
@OptIn(InternalAPI::class)
internal class TruncatedResponseEngine(private val delegate: HttpClientEngine) : HttpClientEngine by delegate {
    private lateinit var channel: ByteReadChannel
    override fun install(client: HttpClient) {
        super<HttpClientEngine>.install(client)
        client.responsePipeline.intercept(HttpResponsePipeline.After) {
            (subject.response as? ByteReadChannel)?.let { channel = it }
        }
    }

    fun awaitFailure() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!channel.isClosedForRead) {
            check(System.nanoTime() < deadline) { "transport did not report truncated response" }
            Thread.sleep(1)
        }
        // Establish closure before the next read, rather than rely on scheduler timing.
        assertIs<IOException>(channel.closedCause)
    }
}
