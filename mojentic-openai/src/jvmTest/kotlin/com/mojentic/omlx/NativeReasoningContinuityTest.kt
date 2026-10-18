package com.mojentic.omlx

import com.mojentic.llm.ChatSession
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.ReasoningEffort
import com.mojentic.llm.ReasoningProfile
import com.mojentic.llm.StreamEvent
import com.mojentic.llm.recovery.RecoveryPolicy
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NativeReasoningContinuityTest {
    @Test
    fun ordinaryAndStreamedToolsPreserveAssistantAssociationOnlyWhenSelected() = runTest {
        for (provider in NativeReasoningCases.providers) {
            for (streamed in listOf(false, true)) {
                for (retain in listOf(false, true)) {
                    for (recovery in listOf(null, RecoveryPolicy())) verifyContinuity(provider, streamed, retain, recovery)
                }
            }
        }
    }

    private suspend fun verifyContinuity(
        provider: RecoveryTestFixtures.Provider,
        streamed: Boolean,
        retain: Boolean,
        recovery: RecoveryPolicy?,
    ) {
        val mode = if (streamed) "stream" else "ordinary"
        val replies = listOf(
            NativeReasoningCases.reply(provider, "${mode}_tool"),
            NativeReasoningCases.reply(provider, "${mode}_final"),
            NativeReasoningCases.reply(provider, "${mode}_final"),
        )
        RecoveryScriptedTransport(replies).use { server ->
            provider.gateway(server.url, engine = server.engine).use { owned ->
                val executed = mutableListOf<String>()
                val config = CompletionConfig(
                    reasoningEffort = ReasoningEffort.XHIGH,
                    reasoningProfile = ReasoningProfile.qwen38(),
                    preserveReasoning = retain,
                    recovery = recovery,
                )
                val session =
                    ChatSession(
                        LlmBroker(owned.value),
                        "not-a-qwen-name",
                        tools = listOf(NativeReasoningCases.tool(executed)),
                        config = config,
                    )
                exerciseSession(session, streamed)
                assertEquals(listOf("alpha", "beta"), executed)
                assertRequests(server, provider, retain)
            }
        }
    }

    private suspend fun exerciseSession(session: ChatSession, streamed: Boolean) {
        if (streamed) {
            val events = session.stream("question").toList()
            assertEquals(
                listOf("native-before-tools", "native-final"),
                events.filterIsInstance<StreamEvent.ThinkingChunk>().map {
                    it.text
                },
            )
            assertEquals(
                listOf("tool-result-alpha", "tool-result-beta"),
                events.filterIsInstance<StreamEvent.ToolResult>().map {
                    it.result
                },
            )
            session.stream("follow-up").toList()
        } else {
            assertEquals("final-answer", session.send("question").content)
            session.send("follow-up")
        }
    }

    private fun assertRequests(server: RecoveryScriptedTransport, provider: RecoveryTestFixtures.Provider, retain: Boolean) {
        val requests = NativeReasoningCases.requests(server)
        val next = NativeReasoningCases.messages(requests[1])
        val historyKey = if (provider == RecoveryTestFixtures.Provider.OLLAMA) "thinking" else "reasoning_content"
        if (retain) {
            assertRetained(next, historyKey, provider)
            val outer = NativeReasoningCases.messages(requests[2])
            assertEquals(next, outer.take(next.size))
            assertEquals(JsonPrimitive("native-final"), outer[next.size][historyKey])
            assertEquals(
                JsonPrimitive("follow-up"),
                outer.last()["content"].let {
                    // OpenAI-style user messages use content parts.
                    if (it is kotlinx.serialization.json.JsonArray) it[0].jsonObject["text"] else it
                },
            )
        } else {
            assertEquals(
                listOf("user", "assistant", "tool", "assistant", "tool"),
                next.map {
                    it.getValue("role").toString().trim('"')
                },
            )
            next.forEach { assertNull(it[historyKey]) }
            NativeReasoningCases.messages(requests[2]).forEach { assertNull(it[historyKey]) }
        }
        requests.forEach { request ->
            if (provider == RecoveryTestFixtures.Provider.OMLX) {
                assertEquals(
                    if (retain) JsonObject(mapOf("preserve_thinking" to JsonPrimitive(true))) else null,
                    request["chat_template_kwargs"],
                )
            } else {
                assertFalse("chat_template_kwargs" in request)
            }
        }
    }

    private fun assertRetained(next: List<JsonObject>, key: String, provider: RecoveryTestFixtures.Provider) {
        assertEquals(listOf("user", "assistant", "tool", "tool"), next.map { it.getValue("role").toString().trim('"') })
        assertEquals(JsonPrimitive("native-before-tools"), next[1][key])
        assertEquals(JsonPrimitive("visible-before-tools"), next[1]["content"])
        val calls = next[1].getValue("tool_calls").jsonArray.map { it.jsonObject }
        assertEquals(listOf(JsonPrimitive("lookup"), JsonPrimitive("lookup")), calls.map { it.getValue("function").jsonObject["name"] })
        assertEquals(listOf(JsonPrimitive("tool-result-alpha"), JsonPrimitive("tool-result-beta")), next.drop(2).map { it["content"] })
        next.drop(2).forEach { assertNull(it[key]) }
        if (provider == RecoveryTestFixtures.Provider.OMLX) {
            assertEquals(listOf(JsonPrimitive("call-alpha"), JsonPrimitive("call-beta")), calls.map { it["id"] })
            assertEquals(calls.map { it["id"] }, next.drop(2).map { it["tool_call_id"] })
        } else {
            assertEquals(calls, next.drop(2).map { it.getValue("tool_calls").jsonArray.single().jsonObject })
        }
    }
}
