package com.mojentic.omlx

import com.mojentic.errors.LlmGatewayException
import com.mojentic.errors.MaxToolIterationsExceededException
import com.mojentic.llm.ChatSession
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.recovery.RecoveryCancellationException
import com.mojentic.llm.recovery.RecoveryEvent
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryReason
import com.mojentic.llm.recovery.RecoveryRetryAfter
import com.mojentic.llm.recovery.RecoveryStage
import com.mojentic.llm.recovery.RecoveryWire
import com.mojentic.omlx.RecoveryTestFixtures.Provider
import com.mojentic.omlx.RecoveryTestFixtures.TEST_TIMEOUT
import com.mojentic.omlx.RecoveryTestFixtures.assertAdmittedRequests
import com.mojentic.omlx.RecoveryTestFixtures.call
import com.mojentic.omlx.RecoveryTestFixtures.countingTool
import com.mojentic.omlx.RecoveryTestFixtures.matrix
import com.mojentic.omlx.RecoveryTestFixtures.messages
import com.mojentic.omlx.RecoveryTestFixtures.policy
import com.mojentic.omlx.RecoveryTestFixtures.schema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Acceptance matrix uses actual public adapters and their default OkHttp engine. */
class CompletionRecoveryHttpTest {
    @Test
    fun recover503PreservesExactPayloadIdentityAndLifecycle(): Unit = runBlocking {
        matrix { provider, structured ->
            val events = mutableListOf<RecoveryEvent>()
            val wires = mutableListOf<RecoveryWire>()
            val sleeps = mutableListOf<Long>()
            RecoveryScriptedServer(
                listOf(ScriptedReply(503, "private-error", mapOf("Retry-After" to "0")), provider.success(structured)),
            ).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val response = call(gateway.value, structured, policy(events, wires, sleeps))
                    assertEquals(if (structured) "{\"value\":\"ok\"}" else "ok", response.content)
                    assertEquals(if (structured && provider == Provider.OLLAMA) null else "native-reasoning", response.thinking)
                    assertEquals("stop", response.finishReason)
                    assertEquals("test-model", response.providerModel)
                    assertNotNull(response.usage)
                    assertEquals(2, server.requests.size)
                    assertContentEquals(server.requests[0], server.requests[1])
                    val requests = wires.filter { it.inspectResponse() == null }
                    assertEquals(listOf(1, 2), requests.map { it.identity.attemptNumber })
                    assertEquals(1, requests.map { it.identity.logicalId }.distinct().size)
                    assertEquals(2, requests.map { it.identity.attemptId }.distinct().size)
                    requests.zip(server.requests).forEach { (wire, bytes) -> assertContentEquals(bytes, wire.inspectRequest()) }
                    assertEquals(listOf(503, 200), wires.filter { it.complete }.map { it.status })
                    assertEquals(
                        listOf(
                            RecoveryStage.STARTED,
                            RecoveryStage.FAILED,
                            RecoveryStage.ADMISSION_PENDING,
                            RecoveryStage.ADMITTED,
                            RecoveryStage.SCHEDULED,
                            RecoveryStage.STARTED,
                            RecoveryStage.SUCCEEDED,
                        ),
                        events.map { it.stage },
                    )
                    assertEquals(listOf(0, 1, 1, 1, 1, 1, 2), events.map { it.wireAttempts })
                    assertTrue(requireNotNull(events.last().progress).contentObserved)
                    events.forEach { assertFalse("private" in Json.encodeToString(it.summary())) }
                    assertEquals(listOf(100L), sleeps)
                    val payload = server.requests[0].decodeToString()
                    assertTrue("prior-assistant" in payload && "private-request" in payload)
                    assertTrue("temperature" in payload)
                    assertTrue(
                        when (provider) {
                            Provider.OLLAMA -> "think" in payload
                            Provider.OMLX -> "reasoning_effort" in payload
                            Provider.OPENAI -> "reasoning_effort" !in payload
                        },
                    )
                    assertTrue(if (structured) "schema-value" in payload else "count-tool" in payload)
                    assertFalse("private" in events.toString())
                    assertFalse("native-reasoning" in wires.toString())
                }
            }
        }
    }

    @Test
    fun exhaustionRetainsEveryAttemptAndTypedCause(): Unit = runBlocking {
        matrix { provider, structured ->
            val replies = List(3) { ScriptedReply(504, "secret-${it + 1}", mapOf("X-Request-Id" to "credential-secret")) }
            RecoveryScriptedServer(replies + provider.success(structured)).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val events = mutableListOf<RecoveryEvent>()
                    val failure = assertFailsWith<RecoveryException> { call(gateway.value, structured, policy(events)) }
                    assertEquals(RecoveryReason.EXHAUSTED, failure.reason)
                    assertEquals(3, failure.wireAttempts)
                    assertEquals(3, server.requests.size)
                    assertEquals(listOf(1, 2, 3), failure.failures.map { it.identity.attemptNumber })
                    assertEquals(listOf(504, 504, 504), failure.failures.map { it.status })
                    assertTrue(failure.failures.all { it.eligible && it.progress.headersReceived && it.progress.rawBytes > 0 })
                    failure.failures.zip(replies).forEach { (failure, reply) ->
                        assertSame(failure.inspectCause(), failure.inspectBoundaryCause())
                        assertTrue(failure.inspectCause() is IllegalStateException)
                        assertContentEquals(reply.body.encodeToByteArray(), failure.inspectBytes())
                    }
                    assertEquals(3, events.filter { it.stage == RecoveryStage.FAILED }.last().failures.size)
                    assertFalse("secret" in Json.encodeToString(failure.summary()))
                    assertEquals(null, failure.cause)
                    assertFalse("secret" in failure.toString() || "secret" in failure.failures.toString() || "secret" in events.toString())
                }
            }
        }
    }

    @Test
    fun retryAfterSecondsDatesInvalidAndMinimumRefusal(): Unit = runBlocking {
        matrix { provider, structured ->
            val cases = listOf(
                "2" to 2000L,
                "Thu, 01 Jan 1970 00:00:03 GMT" to 3000L,
                "Wed, 31 Dec 1969 23:59:59 GMT" to 100L,
                "invalid-secret" to 100L,
                "-1" to 100L,
            )
            for ((header, expected) in cases) {
                val sleeps = mutableListOf<Long>()
                val events = mutableListOf<RecoveryEvent>()
                RecoveryScriptedServer(listOf(ScriptedReply(429, "busy", mapOf("Retry-After" to header)), provider.success(structured)))
                    .use { server ->
                        provider.gateway(server.url).use { gateway ->
                            call(gateway.value, structured, policy(events, sleeps = sleeps, wall = 0))
                            assertEquals(listOf(expected), sleeps)
                            val retry = requireNotNull(events.first { it.stage == RecoveryStage.FAILED }.failure).retryAfter
                            assertTrue(
                                if (header == "invalid-secret" || header == "-1") {
                                    retry is RecoveryRetryAfter.Invalid
                                } else {
                                    retry is RecoveryRetryAfter.Delay
                                },
                            )
                        }
                    }
            }
            for (header in listOf("31", "9223372036854775807", "999999999999999999999999")) {
                RecoveryScriptedServer(listOf(ScriptedReply(429, "busy", mapOf("Retry-After" to header)))).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        val failure = assertFailsWith<RecoveryException> { call(gateway.value, structured, policy()) }
                        assertEquals(RecoveryReason.PROVIDER_MINIMUM, failure.reason)
                        assertEquals(1, server.requests.size)
                    }
                }
            }
        }
    }

    @Test
    fun admissionWaitsForExplicitDecisionAndRejectPreventsResend(): Unit = runBlocking {
        matrix { provider, structured ->
            for (status in listOf(503, 0)) {
                for (allow in listOf(true, false)) {
                    val entered = CompletableDeferred<Unit>()
                    val decision = CompletableDeferred<Boolean>()
                    RecoveryScriptedServer(
                        listOf(ScriptedReply(status, "busy", mapOf("Retry-After" to "0")), provider.success(structured)),
                    ).use { server ->
                        provider.gateway(server.url).use { gateway ->
                            val events = mutableListOf<RecoveryEvent>()
                            val wires = mutableListOf<RecoveryWire>()
                            val recovery =
                                RecoveryPolicy(maxAttempts = 2, observer = { events += it }, capture = { wires += it }, admission = {
                                    entered.complete(Unit)
                                    decision.await()
                                }, jitter = { 0 })
                            val result = async { runCatching { call(gateway.value, structured, recovery) } }
                            withTimeout(TEST_TIMEOUT) { entered.await() }
                            assertEquals(1, server.requests.size)
                            assertFalse(result.isCompleted)
                            assertEquals(
                                listOf(RecoveryStage.STARTED, RecoveryStage.FAILED, RecoveryStage.ADMISSION_PENDING),
                                events.map { it.stage },
                            )
                            assertContentEquals(server.requests.single(), wires.first().inspectRequest())
                            val pendingFailure = events.last().failures.single()
                            assertEquals(if (status == 0) null else 503, pendingFailure.status)
                            assertEquals(wires.first().identity, pendingFailure.identity)
                            decision.complete(allow)
                            val outcome = withTimeout(TEST_TIMEOUT) { result.await() }
                            if (allow) {
                                assertTrue(outcome.isSuccess)
                                assertEquals(2, server.requests.size)
                                assertAdmittedRequests(wires, server.requests, events, pendingFailure)
                            } else {
                                val failure = outcome.exceptionOrNull() as RecoveryException
                                assertEquals(RecoveryReason.ADMISSION_REJECTED, failure.reason)
                                assertEquals(1, failure.wireAttempts)
                                assertEquals(if (status == 0) null else 503, failure.failures.single().status)
                                assertEquals(listOf(RecoveryStage.REJECTED, RecoveryStage.EXHAUSTED), events.takeLast(2).map { it.stage })
                                assertEquals(1, server.requests.size)
                            }
                        }
                    }
                }
            }
            RecoveryScriptedServer(listOf(ScriptedReply(503, "busy", mapOf("Retry-After" to "0")))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val failure = assertFailsWith<RecoveryException> {
                        call(gateway.value, structured, RecoveryPolicy(maxAttempts = 3))
                    }
                    assertEquals(RecoveryReason.ADMISSION_REQUIRED, failure.reason)
                    assertEquals(1, server.requests.size)
                }
            }
        }
    }

    @Test
    fun cancellationWinsDuringActiveRequestAdmissionAndBackoff(): Unit = runBlocking {
        matrix { provider, structured ->
            for (phase in listOf("active", "admission", "backoff")) {
                val release = CountDownLatch(1)
                val entered = CompletableDeferred<Unit>()
                val forever = CompletableDeferred<Unit>()
                val report = AtomicReference<RecoveryCancellationException>()
                val events = mutableListOf<RecoveryEvent>()
                val reply = if (phase == "active") {
                    ScriptedReply(200, "{\"partial\":", truncated = true, release = release)
                } else {
                    ScriptedReply(503, "busy")
                }
                RecoveryScriptedServer(listOf(reply)).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        val recovery = RecoveryPolicy(
                            maxAttempts = 3,
                            admission = {
                                if (phase == "admission") {
                                    entered.complete(Unit)
                                    forever.await()
                                }
                                true
                            },
                            sleep = {
                                entered.complete(Unit)
                                forever.await()
                            },
                            observer = { events += it },
                            capture = { if (phase == "active" && it.inspectResponse()?.isNotEmpty() == true) entered.complete(Unit) },
                        )
                        val job = async {
                            try {
                                call(gateway.value, structured, recovery)
                            } catch (cancelled: RecoveryCancellationException) {
                                report.set(cancelled)
                                throw cancelled
                            }
                        }
                        withTimeout(TEST_TIMEOUT) { entered.await() }
                        job.cancelAndJoin()
                        val cancelled = assertNotNull(report.get())
                        assertEquals(1, cancelled.wireAttempts)
                        assertEquals(1, server.requests.size)
                        assertEquals(RecoveryStage.CANCELLED, events.last().stage)
                        assertEquals(1, events.count { it.stage == RecoveryStage.CANCELLED })
                        assertEquals(1, events.count { it.stage == RecoveryStage.FAILED })
                        assertTrue(events.none { it.stage == RecoveryStage.SUCCEEDED })
                        release.countDown()
                    }
                }
            }
        }
    }

    @Test
    fun permanentTruncationAndPartialSuccessNeverReplay(): Unit = runBlocking {
        matrix { provider, structured ->
            for (status in listOf(400, 401, 403, 200)) {
                val release = CountDownLatch(1)
                val partial = if (status == 200) "{\"message\":{\"content\":\"secret-partial" else "secret-partial"
                RecoveryScriptedServer(listOf(ScriptedReply(status, partial, truncated = true, release = release)))
                    .use { server ->
                        provider.gateway(server.url).use { gateway ->
                            val recovery = RecoveryPolicy(
                                maxAttempts = 3,
                                admission = { true },
                                retryableStatuses = setOf(400, 401, 403, 200),
                                capture = { if (it.inspectResponse()?.isNotEmpty() == true) release.countDown() },
                            )
                            val failure = assertFailsWith<RecoveryException> { call(gateway.value, structured, recovery) }
                            assertEquals(1, server.requests.size)
                            assertEquals(status, failure.failures.single().status)
                            assertEquals(partial, failure.failures.single().inspectBytes().decodeToString())
                            assertTrue(failure.failures.single().inspectCause() is java.io.IOException)
                            assertEquals(status == 200, failure.failures.single().progress.semanticObserved)
                            assertEquals(
                                if (status ==
                                    200
                                ) {
                                    RecoveryReason.PARTIAL_SUCCESS
                                } else {
                                    RecoveryReason.HTTP_PERMANENT
                                },
                                failure.reason,
                            )
                        }
                    }
            }
        }
    }

    @Test
    fun malformedAndCaptureFailuresAreTerminalWithPrivateCauseIdentity(): Unit = runBlocking {
        matrix { provider, structured ->
            RecoveryScriptedServer(listOf(ScriptedReply(200, "malformed-secret"))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val failure = assertFailsWith<RecoveryException> { call(gateway.value, structured, policy()) }
                    assertEquals(RecoveryReason.PROTOCOL, failure.reason)
                    assertEquals(1, server.requests.size)
                    assertTrue(failure.failures.single().progress.replayUnsafe)
                    assertFalse("malformed-secret" in failure.toString())
                }
            }
            for (beforeSend in listOf(true, false)) {
                val original = IllegalStateException("capture-credential-secret")
                RecoveryScriptedServer(listOf(provider.success(structured))).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        val recovery = RecoveryPolicy(maxAttempts = 3, admission = { true }, capture = {
                            if ((it.inspectResponse() == null) == beforeSend) throw original
                        })
                        val failure = assertFailsWith<RecoveryException> { call(gateway.value, structured, recovery) }
                        assertEquals(RecoveryReason.CAPTURE, failure.reason)
                        assertSame(original, failure.failures.single().inspectCause())
                        assertEquals(if (beforeSend) 0 else 1, server.requests.size)
                        assertEquals(if (beforeSend) 0 else 1, failure.wireAttempts)
                        assertEquals(!beforeSend, failure.failures.single().progress.semanticObserved)
                        assertFalse("credential-secret" in failure.toString() || "credential-secret" in failure.failures.toString())
                    }
                }
            }
        }
    }

    @Test
    fun budgetsDoNotTimeoutHealthyGenerationAndPreventLateResend(): Unit = runBlocking {
        matrix { provider, structured ->
            var monotonic = 0L
            RecoveryScriptedServer(listOf(ScriptedReply(503, "busy"), provider.success(structured))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val recovery = RecoveryPolicy(
                        maxAttempts = 2,
                        budgetMillis = 500,
                        monotonicClock = { monotonic },
                        admission = { true },
                        jitter = { 100 },
                        sleep = { monotonic += it },
                        observer = { if (it.stage == RecoveryStage.STARTED && it.identity.attemptNumber == 1) monotonic = 1_000_000 },
                        capture = {
                            if (it.status == 200 && it.complete) monotonic += 1_000_000
                        },
                    )
                    call(gateway.value, structured, recovery)
                    assertEquals(2, server.requests.size)
                }
            }
        }
    }

    @Test
    fun insufficientBudgetRefusesProviderMinimum(): Unit = runBlocking {
        matrix { provider, structured ->
            RecoveryScriptedServer(listOf(ScriptedReply(503, "busy", mapOf("Retry-After" to "2")))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val failure = assertFailsWith<RecoveryException> {
                        call(
                            gateway.value,
                            structured,
                            RecoveryPolicy(
                                maxAttempts = 2,
                                budgetMillis = 100,
                                admission = { true },
                                jitter = { 0 },
                            ),
                        )
                    }
                    assertEquals(RecoveryReason.BUDGET, failure.reason)
                    assertEquals(1, server.requests.size)
                }
            }
        }
    }

    @Test
    fun deadlineIsRecheckedAfterRequestCaptureBeforeResend(): Unit = runBlocking {
        matrix { provider, structured ->
            var wall = 0L
            var requests = 0
            RecoveryScriptedServer(listOf(ScriptedReply(503, "busy"))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val recovery = RecoveryPolicy(
                        maxAttempts = 2,
                        deadlineEpochMillis = 1000,
                        clock = { wall },
                        admission = { true },
                        jitter = { 0 },
                        capture = {
                            if (it.inspectResponse() == null && ++requests == 2) wall = 1000
                        },
                    )
                    val failure = assertFailsWith<RecoveryException> { call(gateway.value, structured, recovery) }
                    assertEquals(RecoveryReason.BUDGET, failure.reason)
                    assertEquals(1, server.requests.size)
                }
            }
        }
    }

    @Test
    fun retriesDisabledPreserveSuccessAndLegacyFailure(): Unit = runBlocking {
        matrix { provider, structured ->
            RecoveryScriptedServer(listOf(provider.success(structured))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val legacy = call(gateway.value, structured, null)
                    assertEquals(if (structured && provider != Provider.OMLX) null else "native-reasoning", legacy.thinking)
                    assertEquals(1, server.requests.size)
                }
            }
            RecoveryScriptedServer(listOf(ScriptedReply(503, "legacy-error"))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    assertFailsWith<LlmGatewayException> { call(gateway.value, structured, null) }
                    assertEquals(1, server.requests.size)
                }
            }
            RecoveryScriptedServer(listOf(ScriptedReply(503, "safe-error"))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val failure = assertFailsWith<RecoveryException> { call(gateway.value, structured, RecoveryPolicy()) }
                    assertEquals(1, failure.wireAttempts)
                    assertEquals(1, server.requests.size)
                }
            }
        }
    }

    @Test
    fun brokerStructuredAndSessionToolCompletionKeepDepthAndExecuteOnce(): Unit = runBlocking {
        for (provider in Provider.entries) {
            RecoveryScriptedServer(listOf(ScriptedReply(503, "busy"), provider.success(true))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    assertEquals(
                        Value("ok"),
                        LlmBroker(gateway.value).completeJson<Value>("test-model", messages, CompletionConfig(recovery = policy())),
                    )
                    assertEquals(2, server.requests.size)
                }
            }
            for (repeatTool in listOf(false, true)) {
                var executions = 0
                val tool = countingTool { executions++ }
                val toolReply = provider.toolReply()
                val finalReply = if (repeatTool) toolReply else provider.success(false)
                RecoveryScriptedServer(listOf(toolReply, ScriptedReply(503, "busy"), finalReply)).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        val session = ChatSession(
                            LlmBroker(gateway.value),
                            "test-model",
                            tools = listOf(tool),
                            config = CompletionConfig(maxToolIterations = 2, recovery = policy()),
                        )
                        if (repeatTool) {
                            assertFailsWith<MaxToolIterationsExceededException> { session.send("private-request") }
                        } else {
                            assertEquals("ok", session.send("private-request").content)
                        }
                        assertEquals(if (repeatTool) 2 else 1, executions)
                        assertEquals(3, server.requests.size)
                        assertContentEquals(server.requests[1], server.requests[2])
                        assertTrue("tool-result" in server.requests[1].decodeToString())
                    }
                }
            }
        }
    }

    @Test
    fun fullJitterCeilingsSaturateAndRedirectsStaySingleRequest(): Unit = runBlocking {
        matrix { provider, structured ->
            for (large in listOf(false, true)) {
                val ceilings = mutableListOf<Long>()
                val sleeps = mutableListOf<Long>()
                val base = if (large) Long.MAX_VALUE - 2 else 100L
                val cap = if (large) Long.MAX_VALUE - 1 else 300L
                val replies = List(3) { ScriptedReply(503, "busy") } + provider.success(structured)
                RecoveryScriptedServer(replies).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        val recovery = RecoveryPolicy(
                            maxAttempts = 4,
                            baseDelayMillis = base,
                            delayCeilingMillis = cap,
                            admission = { true },
                            jitter = {
                                ceilings += it
                                it / 2
                            },
                            sleep = { sleeps += it },
                        )
                        call(gateway.value, structured, recovery)
                        assertEquals(if (large) listOf(base, cap, cap) else listOf(100L, 200L, 300L), ceilings)
                        assertEquals(ceilings.map { it / 2 }, sleeps)
                        assertEquals(4, server.requests.size)
                    }
                }
            }
            RecoveryScriptedServer(listOf(ScriptedReply(302, "redirect", mapOf("Location" to "/redirect")))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val failure = assertFailsWith<RecoveryException> { call(gateway.value, structured, policy()) }
                    assertEquals(302, failure.failures.single().status)
                    assertEquals(1, server.requests.size)
                }
            }
        }
    }

    @Test
    fun captureAccountsForSemanticFieldsBeforeHookFailure(): Unit = runBlocking {
        matrix { provider, structured ->
            val partials = listOf(
                "{\"message\":{\"content\":\"partial-secret" to "content",
                "{\"message\":{\"thinking\":\"partial-secret" to "reasoning",
                "{\"message\":{\"tool_calls\":[{\"function\":" to "tools",
                " \n " to "raw",
            )
            for ((body, field) in partials) {
                val release = CountDownLatch(1)
                val cause = IllegalStateException("capture-secret")
                RecoveryScriptedServer(listOf(ScriptedReply(200, body, truncated = true, release = release))).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        val recovery = RecoveryPolicy(maxAttempts = 3, admission = { true }, capture = {
                            if (it.inspectResponse()?.isNotEmpty() == true) {
                                release.countDown()
                                assertTrue(it.progress.rawBytes > 0)
                                assertEquals(field != "raw", it.progress.semanticObserved)
                                throw cause
                            }
                        })
                        val failure = assertFailsWith<RecoveryException> { call(gateway.value, structured, recovery) }
                        val progress = failure.failures.single().progress
                        assertSame(cause, failure.failures.single().inspectCause())
                        assertEquals(field == "content", progress.contentObserved)
                        assertEquals(field == "reasoning", progress.reasoningObserved)
                        assertEquals(field == "tools", progress.toolFragmentsObserved)
                        assertFalse(progress.semanticDelivered)
                        assertEquals(1, server.requests.size)
                    }
                }
            }
        }
    }

    @Test
    fun completedToolIsNotReplayedWhenNextCompletionExhausts(): Unit = runBlocking {
        for (provider in Provider.entries) {
            var executions = 0
            val replies = listOf(provider.toolReply()) + List(3) { ScriptedReply(504, "busy") }
            RecoveryScriptedServer(replies).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val session = ChatSession(
                        LlmBroker(gateway.value),
                        "test-model",
                        tools = listOf(countingTool { executions++ }),
                        config = CompletionConfig(maxToolIterations = 2, recovery = policy()),
                    )
                    val failure = assertFailsWith<RecoveryException> { session.send("private-request") }
                    assertEquals(3, failure.failures.size)
                    assertEquals(1, executions)
                    assertEquals(4, server.requests.size)
                    server.requests.drop(1).forEach { assertContentEquals(server.requests[1], it) }
                }
            }
        }
    }

    @Test
    fun slowFailureObserverConsumesBudgetAndSuccessObserverFailureCountsWire(): Unit = runBlocking {
        matrix { provider, structured ->
            var time = 0L
            RecoveryScriptedServer(listOf(ScriptedReply(503, "busy"))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val recovery = RecoveryPolicy(
                        maxAttempts = 3,
                        budgetMillis = 100,
                        monotonicClock = { time },
                        observer = { if (it.stage == RecoveryStage.FAILED) time = 1000 },
                        admission = { true },
                    )
                    val failure = assertFailsWith<RecoveryException> { call(gateway.value, structured, recovery) }
                    assertEquals(RecoveryReason.BUDGET, failure.reason)
                    assertEquals(1, server.requests.size)
                }
            }
            val original = IllegalStateException("observer-secret")
            RecoveryScriptedServer(listOf(provider.success(structured))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val recovery = RecoveryPolicy(maxAttempts = 3, admission = { true }, observer = {
                        if (it.stage == RecoveryStage.SUCCEEDED) throw original
                    })
                    val failure = assertFailsWith<RecoveryException> { call(gateway.value, structured, recovery) }
                    assertEquals(RecoveryReason.POLICY_FAILED, failure.reason)
                    assertEquals(1, failure.wireAttempts)
                    assertSame(original, failure.failures.single().inspectCause())
                    assertFalse("observer-secret" in Json.encodeToString(failure.summary()))
                    assertEquals(1, server.requests.size)
                }
            }
        }
    }

    @Serializable
    private data class Value(val value: String)
}
