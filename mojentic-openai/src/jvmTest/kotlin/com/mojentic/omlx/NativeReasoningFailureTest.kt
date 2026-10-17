package com.mojentic.omlx

import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.ChatSession
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.StreamEvent
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryOperation
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NativeReasoningFailureTest {
    @Test
    fun cancellationOnNativeDeltaRollsBackSessionAndNeverExecutesTools() = runTest {
        for (provider in NativeReasoningCases.providers) {
            for (recovery in listOf(null, RecoveryPolicy())) {
                RecoveryScriptedServer(listOf(NativeReasoningCases.reply(provider, "stream_tool"))).use { server ->
                    provider.gateway(server.url).use { owned ->
                        val executed = mutableListOf<String>()
                        val session = ChatSession(
                            LlmBroker(owned.value),
                            "any",
                            tools = listOf(NativeReasoningCases.tool(executed)),
                            config = CompletionConfig(preserveReasoning = true, recovery = recovery),
                        )
                        val native = CompletableDeferred<String>()
                        val collection = launch {
                            session.stream("question").collect {
                                if (it is StreamEvent.ThinkingChunk) {
                                    native.complete(it.text)
                                    awaitCancellation()
                                }
                            }
                        }
                        assertEquals("native-before-tools", native.await())
                        collection.cancelAndJoin()
                        assertTrue(session.messages().isEmpty())
                        assertTrue(executed.isEmpty())
                        assertEquals(1, server.requests.size)
                    }
                }
            }
        }
    }

    @Test
    fun incompleteNativeStreamAndProviderFailureNeverBecomeCompletedHistory() = runTest {
        for (provider in NativeReasoningCases.providers) {
            for (recovery in listOf(null, RecoveryPolicy())) {
                val incomplete = NativeReasoningCases.incompleteStream(provider)
                for (reply in listOf(ScriptedReply(200, incomplete), ScriptedReply(500, "provider-error"))) {
                    RecoveryScriptedServer(listOf(reply)).use { server ->
                        provider.gateway(server.url).use { owned ->
                            val executed = mutableListOf<String>()
                            val session = ChatSession(
                                LlmBroker(owned.value),
                                "any",
                                tools = listOf(NativeReasoningCases.tool(executed)),
                                config = CompletionConfig(preserveReasoning = true, recovery = recovery),
                            )
                            expectFailure(provider, recovery, RecoveryOperation.STREAM) { session.stream("question").toList() }
                            assertTrue(session.messages().isEmpty())
                            assertTrue(executed.isEmpty())
                            assertEquals(1, server.requests.size)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun unfinishedOrdinaryNativeResponsesNeverReachTools() = runTest {
        for (provider in NativeReasoningCases.providers) {
            for (recovery in listOf(null, RecoveryPolicy())) {
                RecoveryScriptedServer(listOf(NativeReasoningCases.unfinishedOrdinary(provider))).use { server ->
                    provider.gateway(server.url).use { owned ->
                        val executed = mutableListOf<String>()
                        val session = ChatSession(
                            LlmBroker(owned.value),
                            "any",
                            tools = listOf(NativeReasoningCases.tool(executed)),
                            config = CompletionConfig(preserveReasoning = true, recovery = recovery),
                        )
                        expectFailure(provider, recovery, RecoveryOperation.COMPLETE) { session.send("question") }
                        assertTrue(session.messages().isEmpty())
                        assertTrue(executed.isEmpty())
                        assertEquals(1, server.requests.size)
                    }
                }
            }
        }
    }

    @Test
    fun failedOrdinaryContinuationRollsBackNativeHistoryWithoutReplayingTools() = runTest {
        for (provider in NativeReasoningCases.providers) {
            for (recovery in listOf(null, RecoveryPolicy())) {
                val replies = listOf(NativeReasoningCases.reply(provider, "ordinary_tool"), ScriptedReply(500, "provider-error"))
                RecoveryScriptedServer(replies).use { server ->
                    provider.gateway(server.url).use { owned ->
                        val executed = mutableListOf<String>()
                        val session = ChatSession(
                            LlmBroker(owned.value),
                            "any",
                            tools = listOf(NativeReasoningCases.tool(executed)),
                            config = CompletionConfig(preserveReasoning = true, recovery = recovery),
                        )
                        expectFailure(provider, recovery, RecoveryOperation.COMPLETE) { session.send("question") }
                        assertTrue(session.messages().isEmpty())
                        assertEquals(listOf("alpha", "beta"), executed)
                        assertEquals(2, server.requests.size)
                        val messages = NativeReasoningCases.messages(NativeReasoningCases.requests(server)[1])
                        val key = if (provider == RecoveryTestFixtures.Provider.OLLAMA) "thinking" else "reasoning_content"
                        assertEquals("\"native-before-tools\"", messages[1][key].toString())
                    }
                }
            }
        }
    }
    private suspend fun expectFailure(
        provider: RecoveryTestFixtures.Provider,
        policy: RecoveryPolicy?,
        operation: RecoveryOperation,
        call: suspend () -> Unit,
    ) {
        if (policy == null) {
            assertFailsWith<LlmGatewayException> { call() }
        } else {
            val failure = assertFailsWith<RecoveryException> { call() }.failures.single()
            assertEquals(operation, failure.operation)
            assertEquals(
                if (provider ==
                    RecoveryTestFixtures.Provider.OLLAMA
                ) {
                    RecoveryProvider.OLLAMA
                } else {
                    RecoveryProvider.OMLX
                },
                failure.provider,
            )
        }
    }
}
