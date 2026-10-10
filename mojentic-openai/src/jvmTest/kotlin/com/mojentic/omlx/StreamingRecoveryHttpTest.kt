package com.mojentic.omlx

import com.mojentic.llm.ChatSession
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.StreamErrorReason
import com.mojentic.llm.StreamEventsGateway
import com.mojentic.llm.recovery.RecoveryEvent
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryReason
import com.mojentic.llm.recovery.RecoveryStage
import com.mojentic.llm.recovery.RecoveryWire
import com.mojentic.omlx.RecoveryTestFixtures.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Real HTTP through both public adapters, both streaming APIs and broker/session boundaries. */
class StreamingRecoveryHttpTest {
    @Test
    fun admittedRetriesPreserveExactPayloadCaptureAndIdentities(): Unit = runBlocking {
        matrix { provider, eventsApi ->
            val events = mutableListOf<RecoveryEvent>()
            val captures = mutableListOf<RecoveryWire>()
            val sleeps = mutableListOf<Long>()
            RecoveryScriptedServer(
                listOf(ScriptedReply(503, "private-response", mapOf("Retry-After" to "0")), provider.streamSuccess()),
            ).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val output = collect(gateway, eventsApi, RecoveryTestFixtures.policy(events, captures, sleeps))
                    assertTrue(output.isNotEmpty())
                    assertEquals(2, server.requests.size)
                    assertContentEquals(server.requests[0], server.requests[1])
                    val sends = captures.filter { it.inspectResponse() == null }
                    assertEquals(2, sends.size)
                    sends.zip(server.requests).forEach { (capture, request) -> assertContentEquals(request, capture.inspectRequest()) }
                    assertEquals(sends[0].identity.logicalId, sends[1].identity.logicalId)
                    assertNotEquals(sends[0].identity.attemptId, sends[1].identity.attemptId)
                    assertEquals(listOf(1, 2), sends.map { it.identity.attemptNumber })
                    assertEquals(
                        listOf("private-response", provider.streamSuccess().body),
                        captures.filter {
                            it.complete
                        }.map { it.inspectResponse()?.decodeToString() },
                    )
                    assertEquals(listOf(100L), sleeps)
                    assertEquals(
                        listOf(
                            RecoveryStage.STARTED,
                            RecoveryStage.FAILED,
                            RecoveryStage.ADMISSION_PENDING,
                            RecoveryStage.ADMITTED,
                            RecoveryStage.SCHEDULED,
                            RecoveryStage.STARTED,
                        ),
                        events.take(6).map {
                            it.stage
                        },
                    )
                    assertEquals(RecoveryStage.SUCCEEDED, events.last().stage)
                    assertEquals(2, events.last().wireAttempts)
                    assertFalse(Json.encodeToString(events.map { it.summary() }).contains("private"))
                    assertFalse(events.toString().contains("credential-secret"))
                }
            }
        }
    }

    @Test
    fun retryAfterAndBoundedGatewayTimeout(): Unit = runBlocking {
        matrix { provider, eventsApi ->
            val sleeps = mutableListOf<Long>()
            RecoveryScriptedServer(
                listOf(ScriptedReply(429, "private-429", mapOf("Retry-After" to "2")), provider.streamSuccess()),
            ).use { server ->
                provider.gateway(server.url).use { gateway ->
                    collect(gateway, eventsApi, RecoveryTestFixtures.policy(sleeps = sleeps))
                    assertEquals(listOf(2000L), sleeps)
                }
            }
            RecoveryScriptedServer(List(3) { ScriptedReply(504, "private-504") }).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val error = assertFailsWith<RecoveryException> { collect(gateway, eventsApi, RecoveryTestFixtures.policy()) }
                    assertEquals(RecoveryReason.EXHAUSTED, error.reason)
                    assertEquals(listOf(504, 504, 504), error.failures.map { it.status })
                    assertEquals(3, error.wireAttempts)
                    assertEquals(3, server.requests.size)
                    assertTrue(error.failures.all { it.inspectBytes().decodeToString() == "private-504" })
                    assertFalse(error.toString().contains("private"))
                }
            }
        }
    }

    @Test
    fun admissionWaitRejectAndRecoveryBudgetDoNotSend(): Unit = runBlocking {
        matrix { provider, eventsApi ->
            val entered = CompletableDeferred<Unit>()
            val decision = CompletableDeferred<Boolean>()
            RecoveryScriptedServer(listOf(ScriptedReply(503, "private-admission"))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    var failure: RecoveryException? = null
                    val job = launch {
                        failure = assertFailsWith<RecoveryException> {
                            collect(
                                gateway,
                                eventsApi,
                                RecoveryPolicy(maxAttempts = 2, jitter = { 0 }, admission = {
                                    entered.complete(Unit)
                                    decision.await()
                                }),
                            )
                        }
                    }
                    withTimeout(5000) { entered.await() }
                    assertEquals(1, server.requests.size)
                    decision.complete(false)
                    withTimeout(5000) { job.join() }
                    assertEquals(RecoveryReason.ADMISSION_REJECTED, failure?.reason)
                    assertEquals(1, server.requests.size)
                }
            }
            RecoveryScriptedServer(listOf(ScriptedReply(503, "private-budget"))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val failure = assertFailsWith<RecoveryException> {
                        collect(
                            gateway,
                            eventsApi,
                            RecoveryPolicy(maxAttempts = 2, budgetMillis = 0, admission = {
                                error("must not admit")
                            }),
                        )
                    }
                    assertEquals(RecoveryReason.BUDGET, failure.reason)
                    assertEquals(1, server.requests.size)
                }
            }
        }
    }

    @Test
    fun allObservedSemanticChannelsInterruptWithoutReplay(): Unit = runBlocking {
        matrix { provider, eventsApi ->
            for (field in listOf("content", "reasoning", "tools")) {
                val released = CountDownLatch(1)
                val events = mutableListOf<RecoveryEvent>()
                val fragment = provider.fragment(field)
                RecoveryScriptedServer(listOf(ScriptedReply(200, fragment, truncated = true, release = released))).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        val failure = assertFailsWith<RecoveryException> {
                            collect(
                                gateway,
                                eventsApi,
                                RecoveryPolicy(maxAttempts = 3, admission = {
                                    error("semantic replay")
                                }, observer = {
                                    events +=
                                        it
                                }, capture = {
                                    if (it.inspectResponse()?.isNotEmpty() == true) released.countDown()
                                }),
                            )
                        }
                        assertEquals(1, server.requests.size)
                        assertTrue(failure.failures.single().progress.semanticObserved)
                        assertEquals(RecoveryStage.INTERRUPTED, events.last().stage)
                        assertTrue(events.none { it.stage == RecoveryStage.SUCCEEDED })
                        assertFalse(failure.toString().contains("private"))
                        if (field ==
                            "content"
                        ) {
                            assertEquals(
                                "private-é".encodeToByteArray().size.toLong(),
                                failure.failures.single().progress.observed.contentBytes,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun keepaliveOnlyClosureRequiresAdmissionAndCanRecover(): Unit = runBlocking {
        matrix { provider, eventsApi ->
            val release = CountDownLatch(1)
            val events = mutableListOf<RecoveryEvent>()
            val keepalive = if (provider == Provider.OMLX) ": keep-alive\n\ndata: {\"model\":\"keepalive\",\"choices\":[]}\n\n" else "\n\n"
            RecoveryScriptedServer(
                listOf(ScriptedReply(200, keepalive, truncated = true, release = release), provider.streamSuccess()),
            ).use { server ->
                provider.gateway(server.url).use { gateway ->
                    collect(
                        gateway,
                        eventsApi,
                        RecoveryPolicy(maxAttempts = 2, jitter = { 0 }, observer = { events += it }, capture = {
                            if (it.inspectResponse()?.isNotEmpty() == true) release.countDown()
                        }, admission = {
                            assertTrue(it.progress.rawBytes > 0)
                            assertFalse(it.progress.semanticObserved)
                            assertEquals(com.mojentic.llm.recovery.RecoveryAcceptance.UNKNOWN, it.acceptance)
                            true
                        }),
                    )
                    assertEquals(2, server.requests.size)
                    assertEquals(RecoveryStage.SUCCEEDED, events.last().stage)
                }
            }
        }
    }

    @Test
    fun truncatedPermanentStatusesRemainPermanent(): Unit = runBlocking {
        matrix { provider, eventsApi ->
            for (status in listOf(400, 401)) {
                val release = CountDownLatch(1)
                RecoveryScriptedServer(listOf(ScriptedReply(status, "private-http", truncated = true, release = release))).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        val failure = assertFailsWith<RecoveryException> {
                            collect(
                                gateway,
                                eventsApi,
                                RecoveryPolicy(maxAttempts = 2, admission = {
                                    error("permanent retry")
                                }, capture = {
                                    if (it.inspectResponse()?.isNotEmpty() == true) release.countDown()
                                }),
                            )
                        }
                        assertEquals(status, failure.failures.single().status)
                        assertEquals(RecoveryReason.HTTP_PERMANENT, failure.reason)
                        assertEquals("private-http", failure.failures.single().inspectBytes().decodeToString())
                        assertTrue(failure.failures.single().inspectCause() is java.io.IOException)
                        assertEquals(1, server.requests.size)
                    }
                }
            }
        }
    }

    @Test
    fun captureFailureIsTerminalBeforeDeliveryThroughBothAdapters(): Unit = runBlocking {
        matrix { provider, eventsApi ->
            val cause = IllegalArgumentException("private-capture")
            val delivered = mutableListOf<Any>()
            RecoveryScriptedServer(listOf(provider.streamSuccess())).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val failure = assertFailsWith<RecoveryException> {
                        collect(
                            gateway,
                            eventsApi,
                            RecoveryPolicy(maxAttempts = 2, admission = { error("capture replay") }, capture = {
                                if (it.progress.semanticObserved) throw cause
                            }),
                            delivered,
                        )
                    }
                    assertTrue(failure.failures.single().inspectCause() === cause)
                    assertEquals(RecoveryReason.CAPTURE, failure.reason)
                    assertTrue(failure.failures.single().progress.semanticObserved)
                    assertFalse(failure.failures.single().progress.semanticDelivered)
                    assertEquals(emptyList(), delivered)
                    assertEquals(1, server.requests.size)
                }
            }
        }
    }

    @Test
    fun validLengthHasProgressThenMetricsThenFailureAndMalformedHasNoTelemetry(): Unit = runBlocking {
        for (eventsApi in listOf(false, true)) {
            val events = mutableListOf<RecoveryEvent>()
            val length =
                """{"message":{"role":"assistant","content":"private-é"},"done":true,""" +
                    """"done_reason":"length","eval_count":4,"total_duration":17}""" +
                    "\n"
            val tools = """"tool_calls":[{"function":{"name":"count-tool","arguments":{}}}],"""
            val lengthWithTools = length.replace("\"content\":", tools + "\"content\":")
            val output = mutableListOf<Any>()
            RecoveryScriptedServer(listOf(ScriptedReply(200, lengthWithTools))).use { server ->
                Provider.OLLAMA.gateway(server.url).use { gateway ->
                    assertFailsWith<RecoveryException> { collect(gateway, eventsApi, RecoveryTestFixtures.policy(events), output) }
                    assertTrue(output.none { it is GatewayStreamEvent.ToolCalls || it is CompletionStreamEvent.Completed })
                    assertEquals(
                        listOf(
                            RecoveryStage.STARTED,
                            RecoveryStage.PROGRESS,
                            RecoveryStage.METRICS,
                            RecoveryStage.FAILED,
                            RecoveryStage.INTERRUPTED,
                        ),
                        events.map {
                            it.stage
                        },
                    )
                    val metrics = requireNotNull(events.first { it.stage == RecoveryStage.METRICS }.metrics)
                    assertEquals(mapOf("eval_count" to 4L), metrics.usage)
                    assertEquals(mapOf("total_duration" to 17L), metrics.durations)
                    assertEquals(1, events[1].frameIndex)
                }
            }
            events.clear()
            RecoveryScriptedServer(listOf(ScriptedReply(200, "{\"done\":true,\"eval_count\":\"invented\"}\n"))).use { server ->
                Provider.OLLAMA.gateway(server.url).use { gateway ->
                    assertFailsWith<RecoveryException> { collect(gateway, eventsApi, RecoveryTestFixtures.policy(events)) }
                    assertTrue(events.none { it.stage == RecoveryStage.PROGRESS || it.stage == RecoveryStage.METRICS })
                    assertEquals(1, server.requests.size)
                }
            }
        }
    }
}

class StreamingRecoveryLifecycleTest {
    @Test
    fun pausedConsumerCancellationRecordsFailedAttemptBeforeOneTerminalCancellation(): Unit = runBlocking {
        matrix { provider, eventsApi ->
            val paused = CompletableDeferred<Unit>()
            val closed = CountDownLatch(1)
            val events = mutableListOf<RecoveryEvent>()
            val body = provider.streamSuccess().body
            RecoveryScriptedServer(listOf(ScriptedReply(200, body, truncated = true, clientClosed = closed))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val job = launch {
                        val config =
                            CompletionConfig(
                                recovery = RecoveryPolicy(maxAttempts = 2, observer = {
                                    events += it
                                }, admission = { error("cancelled retry") }),
                            )
                        if (eventsApi) {
                            (gateway.value as StreamEventsGateway).streamEvents("test", RecoveryTestFixtures.messages, config).collect {
                                if (it is CompletionStreamEvent.Content) {
                                    paused.complete(Unit)
                                    awaitCancellation()
                                }
                            }
                        } else {
                            gateway.value.stream("test", RecoveryTestFixtures.messages, config = config).collect {
                                if (it is GatewayStreamEvent.Content) {
                                    paused.complete(Unit)
                                    awaitCancellation()
                                }
                            }
                        }
                    }
                    withTimeout(5000) { paused.await() }
                    job.cancelAndJoin()
                    assertTrue(closed.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    assertEquals(listOf(RecoveryStage.FAILED, RecoveryStage.CANCELLED), events.takeLast(2).map { it.stage })
                    assertEquals(1, events.count { it.stage == RecoveryStage.CANCELLED })
                    assertTrue(events.none { it.stage == RecoveryStage.SUCCEEDED })
                    val failure = requireNotNull(events.last().failure)
                    assertEquals(200, failure.status)
                    assertTrue(failure.progress.rawBytes > 0)
                    assertTrue(failure.progress.semanticObserved)
                    assertTrue(failure.progress.semanticDelivered)
                    assertEquals(1, server.requests.size)
                }
            }
        }
    }

    @Test
    fun brokerToolRunsOnceAndSessionRestoresSnapshotAfterFollowupFails(): Unit = runBlocking {
        for (provider in Provider.entries) {
            var toolRuns = 0
            val tools = listOf(RecoveryTestFixtures.countingTool { toolRuns++ })
            RecoveryScriptedServer(
                listOf(provider.streamTool(), ScriptedReply(503, "private-followup"), ScriptedReply(504, "private-followup")),
            ).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val session =
                        ChatSession(
                            LlmBroker(gateway.value),
                            "test",
                            systemPrompt = "system",
                            tools = tools,
                            config = CompletionConfig(
                                recovery = RecoveryPolicy(maxAttempts = 2, jitter = {
                                    0
                                }, admission = { true }),
                            ),
                        )
                    val before = session.messages()
                    val failure = assertFailsWith<RecoveryException> { session.stream("private-user").collect {} }
                    assertEquals(2, failure.wireAttempts)
                    assertEquals(1, toolRuns)
                    assertEquals(before, session.messages())
                    assertEquals(3, server.requests.size)
                    assertContentEquals(server.requests[1], server.requests[2])
                    val nativeHistory = server.requests[1].decodeToString()
                    assertTrue(nativeHistory.contains("tool-result"))
                    assertTrue(nativeHistory.contains("count-tool"))
                }
            }
            RecoveryScriptedServer(listOf(ScriptedReply(503, "private-retry"), provider.streamSuccess())).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val session =
                        ChatSession(LlmBroker(gateway.value), "test", config = CompletionConfig(recovery = RecoveryTestFixtures.policy()))
                    session.stream("private-user").collect {}
                    assertEquals(2, session.messages().size)
                }
            }
        }
    }

    @Test
    fun cancellationDuringAdmissionAndBackoffNeverResends(): Unit = runBlocking {
        matrix { provider, eventsApi ->
            for (inAdmission in listOf(false, true)) {
                val waiting = CompletableDeferred<Unit>()
                val events = mutableListOf<RecoveryEvent>()
                RecoveryScriptedServer(listOf(ScriptedReply(503, "private-wait"))).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        val job = launch {
                            collect(
                                gateway,
                                eventsApi,
                                RecoveryPolicy(maxAttempts = 2, observer = { events += it }, admission = {
                                    if (inAdmission) {
                                        waiting.complete(Unit)
                                        awaitCancellation()
                                    }
                                    true
                                }, sleep = {
                                    waiting.complete(Unit)
                                    awaitCancellation()
                                }),
                            )
                        }
                        withTimeout(5000) { waiting.await() }
                        job.cancelAndJoin()
                        assertEquals(1, server.requests.size)
                        assertEquals(1, events.count { it.stage == RecoveryStage.FAILED })
                        assertEquals(1, events.count { it.stage == RecoveryStage.CANCELLED })
                        assertTrue(events.none { it.stage == RecoveryStage.SUCCEEDED })
                        assertEquals(503, events.last().failure?.status)
                    }
                }
            }
        }
    }

    @Test
    fun terminalOnlyMetricsCancellationCannotSucceed(): Unit = runBlocking {
        for (eventsApi in listOf(false, true)) {
            val metricsSeen = CompletableDeferred<Unit>()
            val events = mutableListOf<RecoveryEvent>()
            val terminal = """{"message":{"role":"assistant"},"done":true,"done_reason":"stop","eval_count":2}""" + "\n"
            RecoveryScriptedServer(listOf(ScriptedReply(200, terminal))).use { server ->
                Provider.OLLAMA.gateway(server.url).use { gateway ->
                    lateinit var self: kotlinx.coroutines.Job
                    val job = launch {
                        collect(
                            gateway,
                            eventsApi,
                            RecoveryPolicy(observer = {
                                events += it
                                if (it.stage == RecoveryStage.METRICS) {
                                    metricsSeen.complete(Unit)
                                    self.cancel()
                                }
                            }),
                        )
                    }
                    self = job
                    withTimeout(5000) {
                        metricsSeen.await()
                        job.join()
                    }
                    assertEquals(
                        listOf(
                            RecoveryStage.STARTED,
                            RecoveryStage.PROGRESS,
                            RecoveryStage.METRICS,
                            RecoveryStage.FAILED,
                            RecoveryStage.CANCELLED,
                        ),
                        events.map {
                            it.stage
                        },
                    )
                    assertEquals(200, events.last().failure?.status)
                    assertFalse(requireNotNull(events.last().failure).progress.semanticObserved)
                }
            }
        }
    }

    @Test
    fun pausedTerminalCompletionClosesOwnedResponseBeforeCancellation(): Unit = runBlocking {
        for (provider in Provider.entries) {
            val paused = CompletableDeferred<Unit>()
            val closed = CountDownLatch(1)
            val events = mutableListOf<RecoveryEvent>()
            val body = if (provider == Provider.OLLAMA) {
                """{"message":{"role":"assistant"},"done":true,"done_reason":"stop","eval_count":2}""" + "\n"
            } else {
                "data: " + """{"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"completion_tokens":2}}""" + "\n\ndata: [DONE]\n\n"
            }
            RecoveryScriptedServer(listOf(ScriptedReply(200, body, truncated = true, clientClosed = closed))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val job = launch {
                        (gateway.value as StreamEventsGateway).streamEvents(
                            "test",
                            RecoveryTestFixtures.messages,
                            CompletionConfig(
                                recovery = RecoveryPolicy(observer = {
                                    events +=
                                        it
                                }),
                            ),
                        ).collect {
                            if (it is CompletionStreamEvent.Completed) {
                                paused.complete(Unit)
                                awaitCancellation()
                            }
                        }
                    }
                    withTimeout(5000) { paused.await() }
                    assertEquals(RecoveryStage.SUCCEEDED, events.last().stage)
                    val finalized = events.toList()
                    job.cancelAndJoin()
                    assertTrue(closed.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    assertEquals(finalized, events)
                }
            }
        }
    }

    @Test
    fun retryAfterDatesInvalidAndMinimumRefusalAndHealthyGenerationBudget(): Unit = runBlocking {
        matrix { provider, eventsApi ->
            for ((header, wait) in listOf("Thu, 01 Jan 1970 00:00:02 GMT" to 2000L, "invalid" to 100L)) {
                val sleeps = mutableListOf<Long>()
                RecoveryScriptedServer(
                    listOf(ScriptedReply(429, "private-delay", mapOf("Retry-After" to header)), provider.streamSuccess()),
                ).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        collect(gateway, eventsApi, RecoveryTestFixtures.policy(sleeps = sleeps))
                        assertEquals(listOf(wait), sleeps)
                    }
                }
            }
            RecoveryScriptedServer(listOf(ScriptedReply(429, "private-minimum", mapOf("Retry-After" to "31")))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val failure = assertFailsWith<RecoveryException> { collect(gateway, eventsApi, RecoveryTestFixtures.policy()) }
                    assertEquals(RecoveryReason.PROVIDER_MINIMUM, failure.reason)
                    assertEquals(1, server.requests.size)
                }
            }
            RecoveryScriptedServer(listOf(provider.streamSuccess().copy(delayMillis = 100))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    collect(gateway, eventsApi, RecoveryPolicy(budgetMillis = 0))
                    assertEquals(1, server.requests.size)
                }
            }
        }
    }

    @Test
    fun invalidUtf8AndProviderErrorsCannotManufactureSuccessOrTelemetry(): Unit = runBlocking {
        matrix { provider, eventsApi ->
            val errors = if (provider == Provider.OLLAMA) {
                """{"error":"credential-secret private-echo","message":{"role":"assistant",""" +
                    """"content":"private"},"done":true,"done_reason":"stop","eval_count":99}""" +
                    "\n"
            } else {
                "data: " +
                    """{"error":{"message":"credential-secret private-echo"},""" +
                    """"choices":[{"delta":{"content":"private"},"finish_reason":"stop"}],"usage":{"completion_tokens":99}}""" +
                    "\n\ndata: [DONE]\n\n"
            }
            for (reply in listOf(ScriptedReply(200, errors), ScriptedReply(200, "", rawBody = byteArrayOf(0xc3.toByte(), 0x28, 10)))) {
                val events = mutableListOf<RecoveryEvent>()
                RecoveryScriptedServer(listOf(reply)).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        val failure =
                            assertFailsWith<RecoveryException> { collect(gateway, eventsApi, RecoveryTestFixtures.policy(events)) }
                        assertTrue(
                            events.none {
                                it.stage in listOf(RecoveryStage.PROGRESS, RecoveryStage.METRICS, RecoveryStage.SUCCEEDED)
                            },
                        )
                        assertEquals(1, server.requests.size)
                        assertFalse(failure.toString().contains("private"))
                        assertFalse(events.toString().contains("credential-secret"))
                    }
                }
            }
        }
    }

    @Test
    fun cancellationFromProgressObserverRetainsZeroDeliveredBytes(): Unit = runBlocking {
        matrix { provider, eventsApi ->
            val events = mutableListOf<RecoveryEvent>()
            val delivered = mutableListOf<Any>()
            RecoveryScriptedServer(listOf(provider.streamSuccess())).use { server ->
                provider.gateway(server.url).use { gateway ->
                    lateinit var self: kotlinx.coroutines.Job
                    self = launch {
                        collect(
                            gateway,
                            eventsApi,
                            RecoveryPolicy(observer = {
                                events += it
                                if (it.stage == RecoveryStage.PROGRESS) self.cancel()
                            }),
                            delivered,
                        )
                    }
                    withTimeout(5000) { self.join() }
                    assertEquals(emptyList(), delivered)
                    val failure = requireNotNull(events.last().failure)
                    assertTrue(failure.progress.semanticObserved)
                    assertFalse(failure.progress.semanticDelivered)
                    assertEquals(0, failure.progress.delivered.contentBytes)
                    assertEquals(RecoveryStage.CANCELLED, events.last().stage)
                    assertTrue(events.none { it.stage == RecoveryStage.SUCCEEDED })
                }
            }
        }
    }

    @Test
    fun quotedTelemetryAndTerminationAreMalformedBeforeProgress(): Unit = runBlocking {
        matrix { provider, eventsApi ->
            val normal = provider.streamSuccess().body
            val mutations = if (provider == Provider.OLLAMA) {
                listOf(normal.replace("\"done\":true", "\"done\":\"true\""), normal.replace("\"eval_count\":7", "\"eval_count\":\"7\""))
            } else {
                listOf(normal.replace("\"completion_tokens\":7", "\"completion_tokens\":\"7\""), "unknown-field: value\n" + normal)
            }
            for (body in mutations) {
                val events = mutableListOf<RecoveryEvent>()
                RecoveryScriptedServer(listOf(ScriptedReply(200, body))).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        assertFailsWith<RecoveryException> { collect(gateway, eventsApi, RecoveryTestFixtures.policy(events)) }
                        assertTrue(
                            events.none {
                                it.stage in listOf(RecoveryStage.PROGRESS, RecoveryStage.METRICS, RecoveryStage.SUCCEEDED)
                            },
                        )
                        assertEquals(1, server.requests.size)
                    }
                }
            }
        }
    }

    @Test
    fun brokerTerminalEvidenceCompletesTheActualRecoveryAttempt(): Unit = runBlocking {
        for (provider in Provider.entries) {
            val lifecycle = mutableListOf<RecoveryEvent>()
            val output = mutableListOf<CompletionStreamEvent>()
            RecoveryScriptedServer(listOf(ScriptedReply(503, "private-broker"), provider.streamSuccess())).use { server ->
                provider.gateway(server.url).use { gateway ->
                    LlmBroker(gateway.value).generateStreamEvents(
                        "test",
                        RecoveryTestFixtures.messages,
                        CompletionConfig(recovery = RecoveryTestFixtures.policy(lifecycle)),
                    ).collect { output += it }
                    assertEquals(2, server.requests.size)
                    assertEquals(1, output.filterIsInstance<CompletionStreamEvent.Completed>().size)
                    assertTrue(output.none { it is CompletionStreamEvent.Error })
                    assertEquals(RecoveryStage.SUCCEEDED, lifecycle.last().stage)
                    assertTrue(lifecycle.none { it.stage == RecoveryStage.CANCELLED })
                }
            }
        }
    }

    @Test
    fun finalizationObserverFailureProducesOnlyPrivateErrorThroughGatewayAndBroker(): Unit = runBlocking {
        for (provider in Provider.entries) {
            for (brokerBoundary in listOf(false, true)) {
                val sentinel = IllegalStateException("private-observer-payload")
                val output = mutableListOf<CompletionStreamEvent>()
                RecoveryScriptedServer(listOf(provider.streamSuccess())).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        val config = CompletionConfig(
                            recovery = RecoveryPolicy(observer = {
                                if (it.stage == RecoveryStage.SUCCEEDED) throw sentinel
                            }),
                        )
                        val stream = if (brokerBoundary) {
                            LlmBroker(gateway.value).generateStreamEvents("test", RecoveryTestFixtures.messages, config)
                        } else {
                            (gateway.value as StreamEventsGateway).streamEvents("test", RecoveryTestFixtures.messages, config)
                        }
                        stream.collect { output += it }
                        assertTrue(output.none { it is CompletionStreamEvent.Completed })
                        val error = output.filterIsInstance<CompletionStreamEvent.Error>().single()
                        val failure = (error.reason as StreamErrorReason.RequestFailed).cause as RecoveryException
                        assertSame(sentinel, failure.failures.single().inspectCause())
                        assertFalse(failure.toString().contains("private-observer-payload"))
                        assertEquals(1, server.requests.size)
                    }
                }
            }
        }
    }

    @Test
    fun pausedSessionCancellationRestoresHistoryAndClosesTheAttempt(): Unit = runBlocking {
        for (provider in Provider.entries) {
            val closed = CountDownLatch(1)
            val paused = CompletableDeferred<Unit>()
            val lifecycle = mutableListOf<RecoveryEvent>()
            RecoveryScriptedServer(listOf(provider.streamSuccess().copy(truncated = true, clientClosed = closed))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val session = ChatSession(
                        LlmBroker(gateway.value),
                        "test",
                        systemPrompt = "system",
                        config = CompletionConfig(recovery = RecoveryTestFixtures.policy(lifecycle)),
                    )
                    val before = session.messages()
                    val job = launch {
                        session.stream("private-user").collect {
                            paused.complete(Unit)
                            awaitCancellation()
                        }
                    }
                    withTimeout(5000) { paused.await() }
                    job.cancelAndJoin()
                    assertEquals(before, session.messages())
                    assertTrue(closed.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    assertEquals(listOf(RecoveryStage.FAILED, RecoveryStage.CANCELLED), lifecycle.takeLast(2).map { it.stage })
                    assertTrue(lifecycle.none { it.stage == RecoveryStage.SUCCEEDED })
                }
            }
        }
    }
}

private suspend fun matrix(block: suspend (Provider, Boolean) -> Unit) {
    for (provider in Provider.entries) for (eventsApi in listOf(false, true)) block(provider, eventsApi)
}

private suspend fun collect(
    gateway: RecoveryTestFixtures.OwnedGateway,
    eventsApi: Boolean,
    policy: RecoveryPolicy?,
    output: MutableList<Any> = mutableListOf(),
): List<Any> {
    val config = CompletionConfig(recovery = policy)
    if (eventsApi) {
        (gateway.value as StreamEventsGateway).streamEvents("test", RecoveryTestFixtures.messages, config).collect {
            if (it is CompletionStreamEvent.Error) throw (it.reason as StreamErrorReason.RequestFailed).cause
            output += it
        }
    } else {
        gateway.value.stream("test", RecoveryTestFixtures.messages, config = config).collect { output += it }
    }
    return output
}

private fun Provider.streamSuccess(): ScriptedReply = ScriptedReply(
    200,
    if (this == Provider.OLLAMA) {
        """{"error":null,"model":"test","message":{"role":"assistant","content":"ok-é"},""" +
            """"done":true,"done_reason":"stop","eval_count":7}""" +
            "\n"
    } else {
        "data: " +
            """{"error":null,"model":"test","choices":[{"delta":{"content":"ok-é"},""" +
            """"finish_reason":"stop"}],"usage":{"completion_tokens":7}}""" +
            "\n\ndata: [DONE]\n\n"
    },
)

private fun Provider.fragment(field: String): String {
    val key = when (field) {
        "reasoning" -> if (this ==
            Provider.OLLAMA
        ) {
            "thinking"
        } else {
            "reasoning_content"
        }

        "tools" -> "tool_calls"

        else -> "content"
    }
    val value = if (field != "tools") {
        "\"private-é\""
    } else if (this == Provider.OLLAMA) {
        """[{"function":{"name":"count-tool","arguments":{}}}]"""
    } else {
        """[{"index":0,"id":"call-1","function":{"name":"count-tool","arguments":"{"}}]"""
    }
    return if (this ==
        Provider.OLLAMA
    ) {
        "{\"message\":{\"role\":\"assistant\",\"$key\":$value},\"done\":false}\n"
    } else {
        "data: {\"choices\":[{\"delta\":{\"$key\":$value}}]}\n\n"
    }
}

private fun Provider.streamTool(): ScriptedReply = ScriptedReply(
    200,
    if (this == Provider.OLLAMA) {
        fragment("tools").replace("\"done\":false", "\"done\":true,\"done_reason\":\"stop\"")
    } else {
        "data: " +
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call-1",""" +
            """"function":{"name":"count-tool","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}""" +
            "\n\ndata: [DONE]\n\n"
    },
)
