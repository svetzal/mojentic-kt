package com.mojentic.anthropic

import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.MessageRole
import com.mojentic.llm.ReasoningEffort
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertFailsWith

class NativeReasoningUnsupportedTest {
    @Test
    fun newControlsAndNativeHistoryFailBeforeHttp() = runTest {
        val gateway = AnthropicGateway("key", engine = MockEngine { error("Unexpected HTTP") })
        try {
            val messages = listOf(LlmMessage.user("question"))
            for (config in listOf(
                CompletionConfig(preserveReasoning = true),
                CompletionConfig(reasoningEffort = ReasoningEffort.DISABLED),
                CompletionConfig(reasoningEffort = ReasoningEffort.ENABLED),
                CompletionConfig(reasoningEffort = ReasoningEffort.XHIGH),
            )) {
                assertFailsWith<LlmGatewayException> { gateway.complete("any", messages, config = config) }
                assertFailsWith<LlmGatewayException> { gateway.completeJson("any", messages, JsonObject(emptyMap()), config) }
                assertFailsWith<LlmGatewayException> { gateway.stream("any", messages, config = config).toList() }
            }
            assertFailsWith<LlmGatewayException> {
                gateway.complete("any", listOf(LlmMessage(MessageRole.Assistant, reasoningContent = "native")))
            }
        } finally {
            gateway.close()
        }
    }
}
