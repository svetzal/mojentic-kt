package com.mojentic.omlx

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.recovery.RecoveryEvent
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryStage
import com.mojentic.llm.recovery.RecoveryWire
import com.mojentic.openai.OpenAIGateway
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeterministicRecoveryProofTest {
    @Test
    fun retryPreservesPayloadIdentityFailureAndLifecycle() = runTest {
        val events = mutableListOf<RecoveryEvent>()
        val wires = mutableListOf<RecoveryWire>()
        val sleeps = mutableListOf<Long>()
        val transport = RecoveryScriptedTransport(
            listOf(
                ScriptedReply(503, "private-failure", mapOf("Retry-After" to "0")),
                ScriptedReply(200, SUCCESS),
            ),
            StandardTestDispatcher(testScheduler),
        )
        val gateway = OpenAIGateway("credential-secret", engine = transport.engine)
        try {
            val result = gateway.complete(
                "gpt-4o",
                listOf(LlmMessage.user("private-request")),
                null,
                CompletionConfig(
                    recovery = RecoveryPolicy(
                        maxAttempts = 2,
                        admission = { true },
                        observer = { events += it },
                        capture = { wires += it },
                        clock = { 0 },
                        monotonicClock = { testScheduler.currentTime },
                        sleep = {
                            sleeps += it
                            testScheduler.advanceTimeBy(it)
                        },
                        jitter = { it },
                    ),
                ),
            )
            assertEquals("ok", result.content)
            assertEquals(100L, testScheduler.currentTime, "Recovery backoff must advance the injected clock by 100 ms")
            assertRecovery(transport.requests, events, wires, sleeps)
        } finally {
            gateway.close()
            transport.close()
        }
    }

    private fun assertRecovery(
        requests: List<ByteArray>,
        events: List<RecoveryEvent>,
        wires: List<RecoveryWire>,
        sleeps: List<Long>,
    ) {
        assertEquals(2, requests.size)
        val expected =
            """{"model":"gpt-4o","messages":[{"role":"user","content":[{"type":"text","text":"private-request"}]}]}""".encodeToByteArray()
        requests.forEach { assertContentEquals(expected, it) }
        assertEquals(listOf(100L), sleeps)
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
        val first = events.first().identity
        val second = events.last().identity
        assertEquals(first.logicalId, second.logicalId)
        assertNotEquals(first.attemptId, second.attemptId)
        assertTrue(first.logicalId.isNotBlank())
        assertTrue(first.attemptId.isNotBlank())
        assertTrue(second.attemptId.isNotBlank())
        assertEquals(listOf(1, 2), listOf(first.attemptNumber, second.attemptNumber))
        val failure = events.single { it.stage == RecoveryStage.FAILED }.failure
        assertNotNull(failure)
        assertEquals(503, failure.status)
        assertSame(failure, events.last().failures.single())
        assertNotNull(failure.inspectCause())
        assertSame(failure.inspectCause(), failure.inspectBoundaryCause())
        assertContentEquals("private-failure".encodeToByteArray(), failure.inspectBytes())
        listOf(first, second).forEach { identity ->
            val captures = wires.filter { it.identity == identity }
            captures.forEach { assertContentEquals(expected, it.inspectRequest()) }
            val received = captures.single { it.complete }
            val body = if (identity == first) "private-failure" else SUCCESS
            assertContentEquals(body.encodeToByteArray(), received.inspectResponse())
            assertEquals(if (identity == first) 503 else 200, received.status)
        }
        val formatted = events.toString() + events.map { it.summary() } + wires + failure
        listOf("private", "private-request", "private-failure", "credential-secret").forEach { secret ->
            assertFalse(secret in formatted)
        }
        wires.forEach { wire -> assertFalse(wire.inspectHeaders().keys.any { it.equals("authorization", true) }) }
    }

    private companion object {
        const val SUCCESS = """{"choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}"""
    }
}
