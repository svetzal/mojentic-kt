package com.mojentic.anthropic

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import java.io.IOException
import kotlin.test.assertIs

/** Inspects the injected transport channel after capture has consumed the partial body. */
@OptIn(InternalAPI::class)
internal class TruncatedResponseEngine(private val delegate: HttpClientEngine) : HttpClientEngine by delegate {
    private lateinit var channel: ByteReadChannel
    override fun install(client: HttpClient) {
        super<HttpClientEngine>.install(client)
        client.responsePipeline.intercept(HttpResponsePipeline.After) {
            (subject.response as? ByteReadChannel)?.let { channel = it }
        }
    }

    fun assertFailure() {
        assertIs<IOException>(channel.closedCause)
    }
}
