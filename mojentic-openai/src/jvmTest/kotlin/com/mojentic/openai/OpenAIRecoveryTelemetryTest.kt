package com.mojentic.openai

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.StreamErrorReason
import com.mojentic.llm.recovery.RecoveryCancellationException
import com.mojentic.llm.recovery.RecoveryEvent
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryStage
import com.mojentic.llm.recovery.RecoveryStreamClosedException
import com.mojentic.llm.recovery.RecoveryStreamException
import com.mojentic.omlx.RecoveryScriptedServer
import com.mojentic.omlx.ScriptedReply
import com.mojentic.tracer.LlmResponseEvent
import com.mojentic.tracer.TracerSystem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OpenAIRecoveryTelemetryTest {
    @Test
    fun interruptedBrokerEventsRetainTelemetryAndOriginalRecoveryCause(): Unit = runBlocking {
        for (hasDone in listOf(false, true)) {
            val frame = "data: {\"model\":\"provider-model\",\"choices\":[{\"delta\":{\"content\":\"é\"}," +
                "\"finish_reason\":\"length\"}],\"usage\":{\"completion_tokens\":2}}\n\n"
            val body = frame + if (hasDone) "data: [DONE]\n\n" else ""
            val events = mutableListOf<RecoveryEvent>()
            RecoveryScriptedServer(listOf(ScriptedReply(200, body), ScriptedReply(200, "must-not-replay"))).use { server ->
                val gateway = OpenAIGateway("private-key", server.url)
                try {
                    val tracer = TracerSystem()
                    val output = mutableListOf<CompletionStreamEvent>()
                    LlmBroker(gateway, tracer = tracer).generateStreamEvents(
                        "gpt-4o",
                        listOf(LlmMessage.user("private-payload")),
                        CompletionConfig(recovery = RecoveryPolicy(maxAttempts = 2, observer = { events += it })),
                    ).collect { output += it }
                    assertEquals(CompletionStreamEvent.Content("é"), output.first())
                    val terminal = assertIs<CompletionStreamEvent.Error>(output.last())
                    val failure = assertIs<RecoveryException>(assertIs<StreamErrorReason.RequestFailed>(terminal.reason).cause)
                    val last = failure.failures.single()
                    assertSame(last, events.last().failure)
                    if (hasDone) {
                        assertIs<RecoveryStreamException>(
                            last.inspectCause(),
                        )
                    } else {
                        assertIs<RecoveryStreamClosedException>(last.inspectCause())
                    }
                    val evidence = requireNotNull(last.inspectCompletionEvidence())
                    val response = tracer.eventStore.getEvents().filterIsInstance<LlmResponseEvent>().single()
                    assertEquals("length", evidence.finishReason)
                    assertEquals("provider-model", evidence.providerModel)
                    assertEquals(buildJsonObject { put("completion_tokens", 2) }, evidence.usage)
                    assertEquals(evidence.finishReason, response.finishReason)
                    assertEquals(evidence.providerModel, response.providerModel)
                    assertEquals(evidence.usage, response.usage)
                    assertEquals(
                        listOf(
                            RecoveryStage.STARTED,
                            RecoveryStage.PROGRESS,
                            RecoveryStage.METRICS,
                            RecoveryStage.FAILED,
                            RecoveryStage.INTERRUPTED,
                        ),
                        events.map { it.stage },
                    )
                    assertEquals(1, server.requests.size)
                    assertEquals(2L, last.progress.observed.contentBytes)
                    assertEquals(2L, last.progress.delivered.contentBytes)
                    assertTrue(output.none { it is CompletionStreamEvent.Completed })
                } finally {
                    gateway.close()
                }
            }
        }
    }

    @Test
    fun cancellationFromTerminalUsageObserverClosesResponseWithoutSuccess(): Unit = runBlocking {
        for (surface in listOf("stream", "streamEvents", "brokerEvents")) {
            val body = "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"completion_tokens\":2}}\n\n" +
                "data: [DONE]\n\n"
            val events = mutableListOf<RecoveryEvent>()
            val output = mutableListOf<Any>()
            val closed = CountDownLatch(1)
            RecoveryScriptedServer(
                listOf(ScriptedReply(200, body, truncated = true, clientClosed = closed), ScriptedReply(200, "must-not-replay")),
            ).use { server ->
                val gateway = OpenAIGateway("private-key", server.url)
                try {
                    lateinit var self: Job
                    val config = CompletionConfig(
                        recovery = RecoveryPolicy(maxAttempts = 2, observer = {
                            events += it
                            if (it.stage == RecoveryStage.METRICS) self.cancel()
                        }),
                    )
                    var cancellation: RecoveryCancellationException? = null
                    self = launch {
                        try {
                            collectSurface(surface, gateway, config, output)
                        } catch (cancelled: RecoveryCancellationException) {
                            cancellation = cancelled
                            throw cancelled
                        }
                    }
                    withTimeout(5000) { self.join() }
                    assertEquals(emptyList(), output)
                    assertEquals(
                        listOf(
                            RecoveryStage.STARTED,
                            RecoveryStage.PROGRESS,
                            RecoveryStage.METRICS,
                            RecoveryStage.FAILED,
                            RecoveryStage.CANCELLED,
                        ),
                        events.map { it.stage },
                    )
                    val last = requireNotNull(events.last().failure)
                    assertEquals(200, last.status)
                    assertSame(last.inspectCause(), assertNotNull(cancellation).inspectCancellationCause())
                    assertEquals(listOf(last), assertNotNull(cancellation).failures)
                    assertFalse(last.progress.semanticObserved)
                    assertFalse(last.progress.semanticDelivered)
                    assertEquals("stop", last.inspectCompletionEvidence()?.finishReason)
                    assertEquals(1, server.requests.size)
                    assertTrue(closed.await(5, TimeUnit.SECONDS))
                } finally {
                    gateway.close()
                }
            }
        }
    }

    private suspend fun collectSurface(
        surface: String,
        gateway: OpenAIGateway,
        config: CompletionConfig,
        output: MutableList<Any>,
    ) {
        val messages = listOf(LlmMessage.user("private-payload"))
        when (surface) {
            "streamEvents" -> gateway.streamEvents("gpt-4o", messages, config).collect { output += it }
            "brokerEvents" -> LlmBroker(gateway).generateStreamEvents("gpt-4o", messages, config).collect { output += it }
            else -> gateway.stream("gpt-4o", messages, config = config).collect { output += it }
        }
    }
}
