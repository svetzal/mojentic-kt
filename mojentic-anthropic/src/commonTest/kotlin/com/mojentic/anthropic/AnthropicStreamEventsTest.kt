package com.mojentic.anthropic

import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.StreamErrorReason
import com.mojentic.tracer.TracerSystem
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnthropicStreamEventsTest {
    private lateinit var gateway: AnthropicGateway

    @AfterTest
    fun tearDown() {
        if (::gateway.isInitialized) gateway.close()
    }

    @Test
    fun streamEventsAreUnsupportedWithoutRequestOrTrace() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        gateway = AnthropicGateway(apiKey = "test", engine = engine)
        val tracer = TracerSystem()

        val events = LlmBroker(gateway, tracer)
            .generateStreamEvents("claude-sonnet-4-5", listOf(LlmMessage.user("hi")))
            .toList()

        assertEquals(
            listOf<CompletionStreamEvent>(CompletionStreamEvent.Error(StreamErrorReason.StreamEventsUnsupported)),
            events,
        )
        assertTrue(engine.requestHistory.isEmpty(), "no request may be sent")
        assertEquals(0, tracer.eventStore.size())
    }
}
