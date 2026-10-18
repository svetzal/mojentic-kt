package com.mojentic.anthropic

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.recovery.RecoveryCategory
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryProviderResponseException
import com.mojentic.llm.recovery.RecoveryReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AnthropicRecoveryEvidenceTest {
    private val messages = listOf(LlmMessage.user("private-prompt"))

    @Test
    fun captureFailureRetainsOrdinaryToolTextAndReasoningEvidence(): Unit = runTest {
        val body = """{"content":[{"type":"text","text":"é"},{"type":"thinking","thinking":"思"},""" +
            """{"type":"tool_use","id":"t","name":"count","input":{"private":"argument"}}]}"""
        val cause = IllegalArgumentException("private-cause")
        RecoveryScriptedTransport(listOf(ScriptedReply(200, body))).use { server ->
            val gateway = AnthropicGateway("private-key", server.url, engine = server.engine)
            try {
                val failure = assertFailsWith<RecoveryException> {
                    gateway.complete(
                        "claude",
                        messages,
                        config = CompletionConfig(
                            recovery = RecoveryPolicy(
                                maxAttempts = 3,
                                admission = { error("capture failure must veto replay") },
                                capture = { if (it.inspectResponse()?.isNotEmpty() == true) throw cause },
                            ),
                        ),
                    )
                }
                val attempt = failure.failures.single()
                assertEquals(cause, attempt.inspectCause())
                assertTrue(attempt.progress.toolFragmentsObserved)
                assertEquals(1, attempt.progress.completedToolCallsObserved)
                assertEquals(2L, attempt.progress.observed.contentBytes)
                assertEquals(3L, attempt.progress.observed.reasoningBytes)
                assertFalse(attempt.progress.semanticDelivered)
                assertEquals(1, server.requests.size)
                assertContentEquals(body.encodeToByteArray(), attempt.inspectBytes())
            } finally {
                gateway.close()
            }
        }
    }

    @Test
    fun providerErrorsKeepTypedCauseAndValidatedMetadata(): Unit = runTest {
        val requestId = "req_018EeWyXxfu5pfWkrYcMdjWG"
        val body = "data: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"private-provider\"}}\n\n"
        RecoveryScriptedTransport(listOf(ScriptedReply(200, body, mapOf("request-id" to requestId)))).use { server ->
            val gateway = AnthropicGateway("private-key", server.url, engine = server.engine)
            try {
                val failure = assertFailsWith<RecoveryException> {
                    gateway.stream(
                        "claude",
                        messages,
                        config = CompletionConfig(
                            recovery = RecoveryPolicy(
                                maxAttempts = 3,
                                admission = { error("provider frame cannot authorize replay") },
                            ),
                        ),
                    ).collect {}
                }
                val attempt = failure.failures.single()
                assertEquals(RecoveryCategory.PROVIDER_RESPONSE, attempt.category)
                assertEquals(RecoveryReason.PROVIDER_RESPONSE, attempt.reason)
                assertEquals("overloaded_error", attempt.providerCode)
                assertEquals(requestId, attempt.providerRequestId)
                assertIs<RecoveryProviderResponseException>(attempt.inspectCause())
                assertFalse(attempt.eligible)
                assertFalse(failure.toString().contains("private-provider"))
                assertEquals(1, server.requests.size)
            } finally {
                gateway.close()
            }
        }
    }

    @Test
    fun interruptedCompletedToolsKeepCumulativeCountsAndActualTelemetry(): Unit = runTest {
        fun frame(value: String): String = "data: $value\n\n"
        val start = frame("""{"type":"message_start","message":{"model":"actual-model","content":[],"usage":{"input_tokens":4}}}""")
        val blocks = (0..1).joinToString("") { index ->
            frame(
                """{"type":"content_block_start","index":$index,"content_block":{"type":"tool_use",""" +
                    """"id":"t$index","name":"count","input":{}}}""",
            ) +
                frame("""{"type":"content_block_stop","index":$index}""")
        }
        val rejected = frame("""{"type":"message_delta","delta":{"stop_reason":"max_tokens"},"usage":{"output_tokens":9}}""")
        RecoveryScriptedTransport(listOf(ScriptedReply(200, start + blocks + rejected))).use { server ->
            val gateway = AnthropicGateway("key", server.url, engine = server.engine)
            var deliveries = 0
            try {
                val failure = assertFailsWith<RecoveryException> {
                    gateway.stream(
                        "claude",
                        messages,
                        config = CompletionConfig(
                            recovery = RecoveryPolicy(
                                maxAttempts = 2,
                                admission = { error("completed tools must veto replay") },
                            ),
                        ),
                    ).collect { deliveries++ }
                }
                val attempt = failure.failures.single()
                assertEquals(2, attempt.progress.completedToolCallsObserved)
                assertEquals(2L, attempt.progress.observed.completedToolCalls)
                assertEquals(0L, attempt.progress.delivered.completedToolCalls)
                assertEquals(0, deliveries)
                val evidence = requireNotNull(attempt.inspectCompletionEvidence())
                assertEquals("actual-model", evidence.providerModel)
                assertEquals("max_tokens", evidence.finishReason)
                assertEquals("4", evidence.usage?.get("input_tokens").toString())
                assertEquals("9", evidence.usage?.get("output_tokens").toString())
                assertEquals(1, server.requests.size)
            } finally {
                gateway.close()
            }
        }
    }

    @Test
    fun documentedTerminalReasonsKeepLegacyStreamingResults(): Unit = runTest {
        for (reason in listOf("end_turn", "stop_sequence", "max_tokens", "pause_turn", "refusal", "model_context_window_exceeded")) {
            fun frame(value: String): String = "data: $value\n\n"
            val body = frame("""{"type":"message_start","message":{"content":[]}}""") +
                frame("""{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""") +
                frame("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"é"}}""") +
                frame("""{"type":"content_block_stop","index":0}""") +
                frame("""{"type":"message_delta","delta":{"stop_reason":"$reason"}}""") +
                frame("""{"type":"message_stop"}""")
            RecoveryScriptedTransport(List(2) { ScriptedReply(200, body) }).use { server ->
                val gateway = AnthropicGateway("key", server.url, engine = server.engine)
                try {
                    val legacy = mutableListOf<com.mojentic.llm.GatewayStreamEvent>()
                    val recovered = mutableListOf<com.mojentic.llm.GatewayStreamEvent>()
                    gateway.stream("claude", messages).collect { legacy += it }
                    gateway.stream("claude", messages, config = CompletionConfig(recovery = RecoveryPolicy())).collect { recovered += it }
                    assertEquals(
                        legacy.filterNot { it is GatewayStreamEvent.Completed },
                        recovered.filterNot { it is GatewayStreamEvent.Completed },
                    )
                    assertEquals(reason, assertIs<GatewayStreamEvent.Completed>(legacy.last()).evidence.finishReason)
                    assertEquals(reason, assertIs<GatewayStreamEvent.Completed>(recovered.last()).evidence.finishReason)
                    assertEquals(2, recovered.size)
                    assertEquals(2, server.requests.size)
                } finally {
                    gateway.close()
                }
            }
        }
    }

    @Test
    fun pendingAdmissionWaitsForExplicitPermissionAndBudgetBoundsIt(): Unit = runTest {
        for (bounded in listOf(false, true)) {
            val pending = CompletableDeferred<Unit>()
            val decision = CompletableDeferred<Boolean>()
            RecoveryScriptedTransport(listOf(ScriptedReply(503, ""), ScriptedReply(200, """{"content":[]}"""))).use { server ->
                val gateway = AnthropicGateway("key", server.url, engine = server.engine)
                try {
                    val call = async {
                        runCatching {
                            gateway.complete(
                                "claude",
                                messages,
                                config = CompletionConfig(
                                    recovery = RecoveryPolicy(
                                        maxAttempts = 2,
                                        baseDelayMillis = 0,
                                        jitter = { 0 },
                                        sleep = {},
                                        budgetMillis = if (bounded) 200 else null,
                                        admission = {
                                            pending.complete(Unit)
                                            decision.await()
                                        },
                                    ),
                                ),
                            )
                        }
                    }
                    withTimeout(5000) { pending.await() }
                    assertEquals(1, server.requests.size)
                    assertFalse(call.isCompleted)
                    if (!bounded) decision.complete(true)
                    val result = withTimeout(5000) { call.await() }
                    if (bounded) {
                        val failure = assertIs<RecoveryException>(result.exceptionOrNull())
                        assertEquals(RecoveryReason.BUDGET, failure.reason)
                        assertEquals(1, failure.wireAttempts)
                        assertEquals(1, server.requests.size)
                    } else {
                        assertTrue(result.isSuccess)
                        assertEquals(2, server.requests.size)
                    }
                } finally {
                    gateway.close()
                }
            }
        }
    }

    @Test
    fun redirectsAndOverflowingRetryAfterNeverCreateHiddenAttempts(): Unit = runTest {
        for (status in listOf(307, 429)) {
            RecoveryScriptedTransport(
                listOf(
                    ScriptedReply(
                        status,
                        "",
                        mapOf(
                            "Location" to "/v1/messages",
                            "Retry-After" to "9223372036854775808",
                        ),
                    ),
                ),
            ).use { server ->
                val gateway = AnthropicGateway("key", server.url, engine = server.engine)
                try {
                    val failure = assertFailsWith<RecoveryException> {
                        gateway.complete(
                            "claude",
                            messages,
                            config = CompletionConfig(
                                recovery = RecoveryPolicy(
                                    maxAttempts = 3,
                                    admission = { true },
                                ),
                            ),
                        )
                    }
                    assertEquals(if (status == 307) RecoveryReason.HTTP_PERMANENT else RecoveryReason.PROVIDER_MINIMUM, failure.reason)
                    assertEquals(1, server.requests.size)
                } finally {
                    gateway.close()
                }
            }
        }
    }
}
