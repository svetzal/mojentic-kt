package com.mojentic.anthropic

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryPolicy
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AnthropicRecoveryProofTest {
    @Test
    fun captureFailureRetainsObservedReasoningWithoutDeliveryOrReplay(): Unit = runBlocking {
        val body = "data: {\"type\":\"content_block_delta\",\"index\":0," +
            "\"delta\":{\"type\":\"thinking_delta\",\"thin\\u006bing\":\"private-é\"}}\n\n"
        val delivered = mutableListOf<GatewayStreamEvent>()
        val captureCause = IllegalStateException("private capture failure")
        RecoveryScriptedServer(listOf(ScriptedReply(200, body))).use { server ->
            val gateway = AnthropicGateway("credential-secret", server.url)
            try {
                val failure = assertFailsWith<RecoveryException> {
                    gateway.stream(
                        "claude",
                        listOf(LlmMessage.user("payload-secret")),
                        null,
                        CompletionConfig(
                            recovery = RecoveryPolicy(
                                maxAttempts = 2,
                                admission = { error("observed reasoning must veto admission") },
                                capture = { if (it.inspectResponse()?.isNotEmpty() == true) throw captureCause },
                            ),
                        ),
                    ).collect { delivered += it }
                }
                val attempt = failure.failures.single()
                assertEquals(captureCause, attempt.inspectCause())
                assertTrue(attempt.progress.reasoningObserved)
                assertEquals("private-é".encodeToByteArray().size.toLong(), attempt.progress.observed.reasoningBytes)
                assertEquals(0, delivered.size)
                assertEquals(0L, attempt.progress.delivered.reasoningBytes)
                assertEquals(body.encodeToByteArray().size.toLong(), attempt.progress.rawBytes)
                assertEquals(1, server.requests.size)
                assertTrue(!failure.toString().contains("private"))
            } finally {
                gateway.close()
            }
        }
    }
}
