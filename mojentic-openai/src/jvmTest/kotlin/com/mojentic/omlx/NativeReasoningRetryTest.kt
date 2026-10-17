package com.mojentic.omlx

import com.mojentic.llm.ChatSession
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.StreamEvent
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryFailure
import com.mojentic.llm.recovery.RecoveryOperation
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryProvider
import com.mojentic.llm.recovery.RecoveryReason
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeReasoningRetryTest {
    @Test
    fun partialNativeStreamCannotBeRetriedOrRetainedEvenWithAdmission() = runTest {
        for (provider in NativeReasoningCases.providers) {
            val failed = NativeReasoningCases.incompleteStream(provider)
                .replace("native-before-tools", "failed-native")
                .replace("visible-before-tools", "failed-visible")
            val replies = listOf(ScriptedReply(200, failed), NativeReasoningCases.reply(provider, "stream_tool"))
            RecoveryScriptedServer(replies).use { server ->
                provider.gateway(server.url).use { owned ->
                    val admitted = mutableListOf<RecoveryFailure>()
                    val executed = mutableListOf<String>()
                    val session = ChatSession(
                        LlmBroker(owned.value),
                        "any",
                        tools = listOf(NativeReasoningCases.tool(executed)),
                        config = CompletionConfig(preserveReasoning = true, recovery = policy(admitted)),
                    )
                    val events = mutableListOf<StreamEvent>()
                    val failure = assertFailsWith<RecoveryException> { session.stream("question").toList(events) }
                    assertEquals(RecoveryReason.PARTIAL_SUCCESS, failure.reason)
                    assertTrue(failure.failures.single().progress.reasoningObserved)
                    assertTrue(failure.failures.single().progress.semanticDelivered)
                    assertEquals(listOf("failed-native"), events.filterIsInstance<StreamEvent.ThinkingChunk>().map { it.text })
                    assertTrue(admitted.isEmpty())
                    assertTrue(executed.isEmpty())
                    assertTrue(session.messages().isEmpty())
                    assertEquals(1, server.requests.size)
                }
            }
        }
    }

    @Test
    fun admittedRetryBeforeDeliveryKeepsCompletedNativeHistoryAndToolCounts() = runTest {
        for (provider in NativeReasoningCases.providers) {
            val replies = listOf(
                ScriptedReply(503, "before-native-delivery"),
                NativeReasoningCases.reply(provider, "stream_tool"),
                NativeReasoningCases.reply(provider, "stream_final"),
            )
            RecoveryScriptedServer(replies).use { server ->
                provider.gateway(server.url).use { owned ->
                    val admitted = mutableListOf<RecoveryFailure>()
                    val executed = mutableListOf<String>()
                    val session = ChatSession(
                        LlmBroker(owned.value),
                        "any",
                        tools = listOf(NativeReasoningCases.tool(executed)),
                        config = CompletionConfig(preserveReasoning = true, recovery = policy(admitted)),
                    )
                    val events = session.stream("question").toList()
                    assertEquals(
                        listOf("native-before-tools", "native-final"),
                        events.filterIsInstance<StreamEvent.ThinkingChunk>().map { it.text },
                    )
                    assertEquals(listOf("alpha", "beta"), executed)
                    val failure = admitted.single()
                    assertEquals(RecoveryProvider.valueOf(provider.name), failure.provider)
                    assertEquals(RecoveryOperation.STREAM, failure.operation)
                    assertFalse(failure.progress.semanticDelivered)
                    assertContentEquals(server.requests[0], server.requests[1])
                    val followup = NativeReasoningCases.requests(server)[2]
                    val assistant = NativeReasoningCases.messages(followup)[1]
                    val key = if (provider == RecoveryTestFixtures.Provider.OLLAMA) "thinking" else "reasoning_content"
                    assertEquals(JsonPrimitive("native-before-tools"), assistant[key])
                    assertEquals(JsonPrimitive("visible-before-tools"), assistant["content"])
                }
            }
        }
    }

    private fun policy(admitted: MutableList<RecoveryFailure>): RecoveryPolicy = RecoveryPolicy(
        maxAttempts = 2,
        baseDelayMillis = 0,
        delayCeilingMillis = 0,
        admission = {
            admitted += it
            true
        },
        sleep = {},
        jitter = { 0 },
    )
}
