package com.mojentic.omlx

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.MessageRole
import com.mojentic.llm.ReasoningEffort
import com.mojentic.llm.ReasoningProfile
import com.mojentic.llm.recovery.RecoveryPolicy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NativeReasoningStructuredTest {
    @Test
    fun structuredBrokerPreservesSeparateNativeHistoryAndTemplateRequest() = runTest {
        val history = listOf(
            LlmMessage(MessageRole.Assistant, content = "visible-history", reasoningContent = "native-history"),
            LlmMessage.user("question"),
        )
        for (provider in NativeReasoningCases.providers) {
            for (retain in listOf(false, true)) {
                for (recovery in listOf(null, RecoveryPolicy())) {
                    RecoveryScriptedTransport(List(2) { NativeReasoningCases.reply(provider, "structured") }).use { server ->
                        provider.gateway(server.url, engine = server.engine).use { owned ->
                            val config = CompletionConfig(
                                reasoningEffort = ReasoningEffort.DISABLED,
                                reasoningProfile = ReasoningProfile.qwen38(),
                                preserveReasoning = retain,
                                recovery = recovery,
                            )
                            val response = owned.value.completeJsonResponse("not-qwen", history, NativeReasoningCases.schema, config)
                            val structured = LlmBroker(owned.value).completeJson<Map<String, String>>("not-qwen", history, config)
                            assertEquals(mapOf("answer" to "structured"), structured)
                            assertEquals("native-structured", response.thinking)
                            for (request in NativeReasoningCases.requests(server)) {
                                val key = if (provider == RecoveryTestFixtures.Provider.OLLAMA) "thinking" else "reasoning_content"
                                val assistant = NativeReasoningCases.messages(request).first()
                                assertEquals(JsonPrimitive("native-history"), assistant[key])
                                assertEquals(JsonPrimitive("visible-history"), assistant["content"])
                                if (provider == RecoveryTestFixtures.Provider.OMLX) {
                                    assertEquals(
                                        if (retain) JsonObject(mapOf("preserve_thinking" to JsonPrimitive(true))) else null,
                                        request["chat_template_kwargs"],
                                    )
                                    assertEquals(JsonPrimitive(false), request["enable_thinking"])
                                    assertNull(request["reasoning_effort"])
                                } else {
                                    assertEquals(JsonPrimitive(false), request["think"])
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
