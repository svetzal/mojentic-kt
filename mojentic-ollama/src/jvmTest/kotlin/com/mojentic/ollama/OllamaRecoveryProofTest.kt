package com.mojentic.ollama

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OllamaRecoveryProofTest {
    @Test
    fun truncatedPermanentResponseCannotBecomeTransportRetry(): Unit = runTest {
        val release = CompletableDeferred<Unit>()
        val transport = RecoveryScriptedTransport(listOf(ScriptedReply(400, "secret-partial", truncated = true, release = release)))
        val gateway = OllamaGateway(transport.url, engine = transport.engine)
        try {
            val failure = assertFailsWith<RecoveryException> {
                gateway.complete(
                    "test",
                    listOf(LlmMessage.user("private-request")),
                    config = CompletionConfig(
                        recovery = RecoveryPolicy(
                            maxAttempts = 2,
                            admission = {
                                true
                            },
                            jitter = { 0 },
                            clock = { 0 },
                            monotonicClock = { 0 },
                            sleep = {},
                            capture = { if (it.inspectResponse()?.isNotEmpty() == true) release.complete(Unit) },
                        ),
                    ),
                )
            }
            assertEquals(1, transport.requests.size)
            assertEquals(400, failure.failures.single().status)
            assertEquals("secret-partial", failure.failures.single().inspectBytes().decodeToString())
            assertTrue(failure.failures.single().inspectCause() is java.io.IOException)
            assertTrue("secret" !in failure.toString())
        } finally {
            gateway.close()
            transport.close()
        }
    }

    @Test
    fun observedStreamingContentBlocksReplayBeforeCaptureDelivery(): Unit = runTest {
        val release = CompletableDeferred<Unit>()
        val body = """{"message":{"role":"assistant","content":"secret-é"},"done":false}""" + "\n"
        val transport = RecoveryScriptedTransport(listOf(ScriptedReply(200, body, truncated = true, release = release)))
        val gateway = OllamaGateway(transport.url, engine = transport.engine)
        val captureCause = IllegalStateException("private-capture")
        val delivered = mutableListOf<String>()
        try {
            val failure = assertFailsWith<RecoveryException> {
                gateway.stream(
                    "test",
                    listOf(LlmMessage.user("private-request")),
                    config = CompletionConfig(
                        recovery = RecoveryPolicy(
                            maxAttempts = 2,
                            admission = { true },
                            capture = {
                                if (it.progress.semanticObserved) {
                                    release.complete(Unit)
                                    throw captureCause
                                }
                            },
                        ),
                    ),
                ).collect { delivered += it.toString() }
            }
            assertEquals(
                com.mojentic.llm.recovery.RecoveryReason.CAPTURE,
                failure.reason,
                failure.failures.single().summary().toString() + failure.failures.single().inspectBytes().decodeToString(),
            )
            assertEquals(1, transport.requests.size)
            assertEquals(emptyList(), delivered)
            assertTrue(failure.failures.single().progress.contentObserved)
            assertTrue(!failure.failures.single().progress.semanticDelivered)
            assertTrue(failure.failures.single().inspectCause() === captureCause)
            assertTrue("private" !in failure.toString())
        } finally {
            gateway.close()
            transport.close()
        }
    }
}
