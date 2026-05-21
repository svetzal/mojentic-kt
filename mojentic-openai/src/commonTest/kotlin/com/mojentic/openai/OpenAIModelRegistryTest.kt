package com.mojentic.openai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenAIModelRegistryTest {
    @Test
    fun gpt54FamilyResolvesAsReasoningModels() {
        val flagship = listOf("gpt-5.4", "gpt-5.4-2026-03-05")
        for (model in flagship) {
            val info = assertNotNull(OpenAIModelRegistry.info(model), "expected entry for $model")
            assertEquals(1_050_000, info.contextWindow, "context window for $model")
            assertTrue(info.supportsReasoningEffort, "$model should support reasoning effort")
            assertTrue(info.supportsTools, "$model should support tools")
            assertTrue(info.supportsVision, "$model should support vision")
        }
    }

    @Test
    fun gpt54MiniAndNanoUseFourHundredKContextWindow() {
        val smaller = listOf(
            "gpt-5.4-mini",
            "gpt-5.4-mini-2026-03-17",
            "gpt-5.4-nano",
            "gpt-5.4-nano-2026-03-17",
        )
        for (model in smaller) {
            val info = assertNotNull(OpenAIModelRegistry.info(model), "expected entry for $model")
            assertEquals(400_000, info.contextWindow, "context window for $model")
            assertTrue(info.supportsReasoningEffort, "$model should support reasoning effort")
            assertTrue(info.supportsTools, "$model should support tools")
            assertTrue(info.supportsVision, "$model should support vision")
        }
    }

    @Test
    fun gpt55FamilyResolvesAsReasoningModels() {
        val models = listOf(
            "gpt-5.5",
            "gpt-5.5-2026-04-23",
            "gpt-5.5-pro",
            "gpt-5.5-pro-2026-04-23",
        )
        for (model in models) {
            val info = assertNotNull(OpenAIModelRegistry.info(model), "expected entry for $model")
            assertEquals(1_050_000, info.contextWindow, "context window for $model")
            assertTrue(info.supportsReasoningEffort, "$model should support reasoning effort")
            assertTrue(info.supportsTools, "$model should support tools")
            assertTrue(info.supportsVision, "$model should support vision")
        }
    }

    @Test
    fun newModelsExposeReasoningEffortThroughHelper() {
        assertTrue(OpenAIModelRegistry.supportsReasoningEffort("gpt-5.5-pro"))
        assertTrue(OpenAIModelRegistry.supportsTools("gpt-5.4-mini"))
        assertTrue(OpenAIModelRegistry.supportsVision("gpt-5.5"))
    }
}
