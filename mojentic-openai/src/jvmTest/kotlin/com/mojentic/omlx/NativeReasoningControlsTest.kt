package com.mojentic.omlx

import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.MessageRole
import com.mojentic.llm.ReasoningEffort
import com.mojentic.llm.ReasoningProfile
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.openai.OpenAIGateway
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeReasoningControlsTest {
    @Test
    fun contractIdentity() {
        assertEquals("native-reasoning-v1", NativeReasoningCases.contract.getValue("version").jsonPrimitive.content)
        assertEquals(
            "d5539eb334b69e0f8b698e8cd2c1402772dfabd09af92c652e1d1dc35f34e127",
            MessageDigest.getInstance("SHA-256").digest(NativeReasoningCases.artifact).joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun everyEffortAndOmissionHasExactWireTypeAcrossCompletionModes() = runTest {
        for (provider in NativeReasoningCases.providers) {
            for (case in NativeReasoningCases.contract.getValue("controls").jsonArray.map { it.jsonObject }) {
                for (mode in listOf("ordinary_final", "structured", "stream_final")) {
                    for (recovery in listOf(null, RecoveryPolicy())) {
                        RecoveryScriptedTransport(listOf(NativeReasoningCases.reply(provider, mode))).use { server ->
                            provider.gateway(server.url, engine = server.engine).use { owned ->
                                val effort = case["effort"].takeUnless { it == JsonNull }?.jsonPrimitive?.content
                                val config = CompletionConfig(
                                    reasoningEffort = effort?.let { ReasoningEffort.valueOf(it.uppercase()) },
                                    recovery = recovery,
                                )
                                val response = when (mode) {
                                    "structured" -> owned.value.completeJsonResponse(
                                        "qwen3.8-o3-misleading",
                                        NativeReasoningCases.messages,
                                        NativeReasoningCases.schema,
                                        config,
                                    )

                                    "stream_final" -> {
                                        val events = owned.value.stream(
                                            "qwen3.8-o3-misleading",
                                            NativeReasoningCases.messages,
                                            config = config,
                                        ).toList()
                                        assertEquals(
                                            listOf("native-final"),
                                            events.filterIsInstance<GatewayStreamEvent.Thinking>().map {
                                                it.text
                                            },
                                        )
                                        null
                                    }

                                    else -> owned.value.complete("qwen3.8-o3-misleading", NativeReasoningCases.messages, config = config)
                                }
                                if (mode ==
                                    "structured"
                                ) {
                                    assertEquals(JsonPrimitive("structured"), response?.structuredJson?.jsonObject?.get("answer"))
                                }
                                if (response !=
                                    null
                                ) {
                                    assertEquals(if (mode == "structured") "native-structured" else "native-final", response.thinking)
                                }
                                val request = NativeReasoningCases.requests(server).single()
                                val expected = case.getValue(provider.name.lowercase()).jsonObject
                                val controls = request.filterKeys {
                                    it in
                                        setOf("think", "enable_thinking", "reasoning_effort", "chat_template_kwargs")
                                }
                                assertEquals(expected, JsonObject(controls))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun profilesValidateExactQwenSetAndHistoryBeforeHttpAcrossModes() = runTest {
        val qwen = ReasoningProfile.qwen38()
        for (effort in ReasoningEffort.entries + null) {
            if (effort in setOf(ReasoningEffort.ENABLED, ReasoningEffort.HIGH)) {
                assertFailsWith<LlmGatewayException> { qwen.validate(effort, true) }
            } else {
                qwen.validate(effort, true)
            }
        }
        val noHistory = ReasoningProfile("boolean-only", setOf(ReasoningEffort.DISABLED, ReasoningEffort.ENABLED), false)
        noHistory.validate(ReasoningEffort.ENABLED, false)
        assertFailsWith<LlmGatewayException> { noHistory.validate(ReasoningEffort.LOW, false) }
        for (provider in NativeReasoningCases.providers) {
            for (config in listOf(
                CompletionConfig(reasoningProfile = qwen, reasoningEffort = ReasoningEffort.HIGH),
                CompletionConfig(reasoningProfile = qwen, reasoningEffort = ReasoningEffort.ENABLED),
                CompletionConfig(reasoningProfile = noHistory, preserveReasoning = true),
            )) {
                RecoveryScriptedTransport(emptyList()).use { server ->
                    provider.gateway(server.url, engine = server.engine).use { owned ->
                        assertFailsWith<LlmGatewayException> {
                            owned.value.complete("anything", NativeReasoningCases.messages, config = config)
                        }
                        assertFailsWith<LlmGatewayException> {
                            owned.value.completeJson("anything", NativeReasoningCases.messages, NativeReasoningCases.schema, config)
                        }
                        assertFailsWith<LlmGatewayException> {
                            LlmBroker(owned.value).stream("anything", NativeReasoningCases.messages, config = config).toList()
                        }
                        val native = listOf(LlmMessage(MessageRole.Assistant, reasoningContent = "native-history"))
                        assertFailsWith<LlmGatewayException> {
                            owned.value.complete("anything", native, config = CompletionConfig(reasoningProfile = noHistory))
                        }
                        assertTrue(server.requests.isEmpty())
                    }
                }
            }
        }
    }

    @Test
    fun openaiRejectsNewControlsAndOmlxHistoryExtensionBeforeHttp() = runTest {
        RecoveryScriptedTransport(emptyList()).use { server ->
            val gateway = OpenAIGateway("key", server.url, engine = server.engine)
            try {
                for (config in listOf(
                    CompletionConfig(preserveReasoning = true),
                    CompletionConfig(reasoningProfile = ReasoningProfile.qwen38(), reasoningEffort = ReasoningEffort.HIGH),
                    CompletionConfig(reasoningEffort = ReasoningEffort.DISABLED),
                    CompletionConfig(reasoningEffort = ReasoningEffort.ENABLED),
                    CompletionConfig(reasoningEffort = ReasoningEffort.XHIGH),
                )) {
                    assertFailsWith<LlmGatewayException> { gateway.complete("o3", NativeReasoningCases.messages, config = config) }
                    assertFailsWith<LlmGatewayException> {
                        gateway.completeJson("o3", NativeReasoningCases.messages, NativeReasoningCases.schema, config)
                    }
                    assertFailsWith<LlmGatewayException> { gateway.stream("o3", NativeReasoningCases.messages, config = config).toList() }
                }
                val history = listOf(LlmMessage(MessageRole.Assistant, content = "visible", reasoningContent = "native"))
                assertFailsWith<LlmGatewayException> { gateway.complete("o3", history) }
                assertFalse(server.requests.isNotEmpty())
            } finally {
                gateway.close()
            }
        }
    }
}
