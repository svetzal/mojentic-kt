package com.mojentic.openai

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.recovery.RecoveryEvent
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryReason
import com.mojentic.llm.recovery.RecoveryStage
import com.mojentic.llm.recovery.RecoveryStreamClosedException
import com.mojentic.llm.recovery.RecoveryWire
import com.mojentic.omlx.RecoveryScriptedServer
import com.mojentic.omlx.ScriptedReply
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpenAIRecoveryProofTest {
    @Test
    fun escapedSemanticOutputInterruptsAtRealOpenAIBoundary(): Unit = runBlocking {
        val body = "data: {\"choices\":[{\"delta\":{\"cont\\u0065nt\":\"private-é\"}}]}\n\n"
        val events = mutableListOf<RecoveryEvent>()
        val wires = mutableListOf<RecoveryWire>()
        val output = mutableListOf<GatewayStreamEvent>()
        RecoveryScriptedServer(
            listOf(
                ScriptedReply(200, body),
                ScriptedReply(
                    200,
                    "data: {\"choices\":[{\"delta\":{\"content\":\"replayed\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n",
                ),
            ),
        ).use { server ->
            val gateway = OpenAIGateway("credential-secret", server.url)
            try {
                val failure = assertFailsWith<RecoveryException> {
                    gateway.stream(
                        "gpt-4o",
                        listOf(LlmMessage.user("payload-secret")),
                        null,
                        CompletionConfig(
                            recovery = RecoveryPolicy(
                                maxAttempts = 2,
                                admission = { error("semantic output must prevent admission") },
                                observer = { events += it },
                                capture = { wires += it },
                            ),
                        ),
                    ).collect { output += it }
                }
                assertEquals(listOf<GatewayStreamEvent>(GatewayStreamEvent.Content("private-é")), output)
                assertEquals(RecoveryReason.PARTIAL_SUCCESS, failure.reason)
                val last = failure.failures.single()
                assertIs<RecoveryStreamClosedException>(last.inspectCause())
                assertEquals(body.encodeToByteArray().size.toLong(), last.progress.rawBytes)
                assertEquals("private-é".encodeToByteArray().size.toLong(), last.progress.observed.contentBytes)
                assertEquals(last.progress.observed.contentBytes, last.progress.delivered.contentBytes)
                assertTrue(last.progress.replayUnsafe)
                assertEquals(1, server.requests.size)
                assertContentEquals(server.requests.single(), wires.first().inspectRequest())
                assertContentEquals(body.encodeToByteArray(), wires.last().inspectResponse())
                assertEquals(last.identity, wires.last().identity)
                assertEquals(listOf(last), events.last().failures)
                assertEquals(RecoveryStage.INTERRUPTED, events.last().stage)
            } finally {
                gateway.close()
            }
        }
    }
}
