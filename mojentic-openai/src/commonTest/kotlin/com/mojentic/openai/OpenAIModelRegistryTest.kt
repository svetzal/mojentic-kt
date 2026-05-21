package com.mojentic.openai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenAIModelRegistryTest {
    // -- gpt-5.4 / gpt-5.5 era (commit fa2f507 entries) ----------------------

    @Test
    fun gpt54FamilyResolvesAsReasoningModels() {
        val flagship = listOf("gpt-5.4", "gpt-5.4-2026-03-05")
        for (model in flagship) {
            val info = assertNotNull(OpenAIModelRegistry.info(model), "expected entry for $model")
            assertEquals(ModelType.REASONING, info.modelType, "model type for $model")
            assertEquals(1_050_000, info.contextWindow, "context window for $model")
            assertEquals(128_000, info.maxOutputTokens, "max output for $model")
            assertTrue(info.supportsReasoningEffort, "$model should support reasoning effort")
            assertTrue(info.supportsTools, "$model should support tools")
            assertTrue(info.supportsVision, "$model should support vision")
            assertTrue(info.supportsChatApi, "$model should support chat api")
            assertTrue(info.supportsResponsesApi, "$model should support responses api")
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

    // -- Model-type classification ------------------------------------------

    @Test
    fun reasoningModelsAreClassifiedAsReasoning() {
        for (model in listOf("o1", "o3", "o3-mini", "o4-mini", "gpt-5", "gpt-5.1", "gpt-5.2")) {
            val info = assertNotNull(OpenAIModelRegistry.info(model), "expected entry for $model")
            assertEquals(ModelType.REASONING, info.modelType, "model type for $model")
            assertTrue(OpenAIModelRegistry.isReasoningModel(model), "$model is a reasoning model")
        }
    }

    @Test
    fun chatModelsAreClassifiedAsChat() {
        for (model in listOf("gpt-4", "gpt-4o", "gpt-4.1", "gpt-3.5-turbo", "gpt-5-chat-latest")) {
            val info = assertNotNull(OpenAIModelRegistry.info(model), "expected entry for $model")
            assertEquals(ModelType.CHAT, info.modelType, "model type for $model")
            assertFalse(OpenAIModelRegistry.isReasoningModel(model), "$model is not reasoning")
        }
    }

    @Test
    fun embeddingModelsAreClassifiedAsEmbedding() {
        for (model in listOf("text-embedding-3-large", "text-embedding-3-small", "text-embedding-ada-002")) {
            val info = assertNotNull(OpenAIModelRegistry.info(model), "expected entry for $model")
            assertEquals(ModelType.EMBEDDING, info.modelType, "model type for $model")
            assertNull(info.contextWindow, "embedding models have no context window")
        }
    }

    // -- gpt-5 base families (previously missing entirely) -------------------

    @Test
    fun gpt5BaseFamiliesAreRegistered() {
        for (model in listOf("gpt-5", "gpt-5-2025-08-07", "gpt-5-pro", "gpt-5.1", "gpt-5.2")) {
            val info = assertNotNull(OpenAIModelRegistry.info(model), "expected entry for $model")
            assertEquals(ModelType.REASONING, info.modelType, "model type for $model")
        }
    }

    @Test
    fun gpt5FlagshipUsesThreeHundredKContextWindow() {
        val info = assertNotNull(OpenAIModelRegistry.info("gpt-5"))
        assertEquals(300_000, info.contextWindow)
        assertEquals(50_000, info.maxOutputTokens)
    }

    @Test
    fun gpt5MiniAndNanoUseTwoHundredKContextWindow() {
        for (model in listOf("gpt-5-mini", "gpt-5-nano")) {
            val info = assertNotNull(OpenAIModelRegistry.info(model))
            assertEquals(200_000, info.contextWindow, "context window for $model")
            assertEquals(32_768, info.maxOutputTokens, "max output for $model")
        }
    }

    // -- Token windows ------------------------------------------------------

    @Test
    fun gpt4oUsesOneTwentyEightKContextWindow() {
        val info = assertNotNull(OpenAIModelRegistry.info("gpt-4o"))
        assertEquals(128_000, info.contextWindow)
        assertEquals(16_384, info.maxOutputTokens)
    }

    @Test
    fun tokenLimitParamMatchesModelType() {
        assertEquals("max_completion_tokens", assertNotNull(OpenAIModelRegistry.info("o3")).tokenLimitParam)
        assertEquals("max_tokens", assertNotNull(OpenAIModelRegistry.info("gpt-4o")).tokenLimitParam)
    }

    // -- Per-API support flags ----------------------------------------------

    @Test
    fun proAndDeepResearchModelsAreResponsesOnly() {
        for (model in listOf("o3-pro", "gpt-5-pro", "o3-deep-research", "gpt-5-codex")) {
            val info = assertNotNull(OpenAIModelRegistry.info(model), "expected entry for $model")
            assertTrue(info.supportsResponsesApi, "$model should support responses api")
            assertFalse(info.supportsChatApi, "$model should not support chat api")
        }
    }

    @Test
    fun gpt51SupportsBothChatAndCompletionsApis() {
        val info = assertNotNull(OpenAIModelRegistry.info("gpt-5.1"))
        assertTrue(info.supportsChatApi)
        assertTrue(info.supportsCompletionsApi)
    }

    @Test
    fun legacyModelsAreCompletionsOnly() {
        for (model in listOf("babbage-002", "davinci-002")) {
            val info = assertNotNull(OpenAIModelRegistry.info(model), "expected entry for $model")
            assertTrue(info.supportsCompletionsApi, "$model should support completions api")
            assertFalse(info.supportsChatApi, "$model should not support chat api")
        }
    }

    @Test
    fun codexMiniLatestIsResponsesOnly() {
        val info = assertNotNull(OpenAIModelRegistry.info("codex-mini-latest"))
        assertTrue(info.supportsResponsesApi)
        assertFalse(info.supportsChatApi)
        assertFalse(info.supportsCompletionsApi)
    }

    // -- Temperature support ------------------------------------------------

    @Test
    fun reasoningModelsOnlyPermitTemperatureOne() {
        val info = assertNotNull(OpenAIModelRegistry.info("o3"))
        assertTrue(info.supportsTemperature(1.0))
        assertFalse(info.supportsTemperature(0.0))
        assertFalse(info.supportsTemperature(0.7))
    }

    @Test
    fun chatModelsPermitAllTemperatures() {
        val info = assertNotNull(OpenAIModelRegistry.info("gpt-4o"))
        assertTrue(info.supportsTemperature(0.0))
        assertTrue(info.supportsTemperature(1.0))
    }

    @Test
    fun searchModelsForbidTemperatureParameter() {
        val info = assertNotNull(OpenAIModelRegistry.info("gpt-4o-search-preview"))
        assertFalse(info.supportsTemperature(1.0))
        assertFalse(info.supportsTemperature(0.0))
    }

    // -- Streaming / tool / vision flags ------------------------------------

    @Test
    fun audioModelsDoNotSupportStreaming() {
        val info = assertNotNull(OpenAIModelRegistry.info("gpt-4o-audio-preview"))
        assertFalse(info.supportsStreaming)
    }

    @Test
    fun searchAndAudioModelsDoNotSupportTools() {
        for (model in listOf("gpt-4o-search-preview", "gpt-4o-audio-preview", "chatgpt-4o-latest", "gpt-4.1-nano")) {
            assertFalse(OpenAIModelRegistry.supportsTools(model), "$model should not support tools")
        }
    }

    @Test
    fun instructModelsAreCompletionsOnly() {
        val info = assertNotNull(OpenAIModelRegistry.info("gpt-3.5-turbo-instruct"))
        assertFalse(info.supportsChatApi)
        assertTrue(info.supportsCompletionsApi)
        assertFalse(info.supportsTools)
    }

    // -- Pattern-matching fallback for unknown models -----------------------

    @Test
    fun unknownGpt5VariantInfersReasoningViaPattern() {
        assertNull(OpenAIModelRegistry.info("gpt-5.9-experimental"))
        val caps = OpenAIModelRegistry.capabilitiesFor("gpt-5.9-experimental")
        assertEquals(ModelType.REASONING, caps.modelType)
        assertTrue(OpenAIModelRegistry.isReasoningModel("gpt-5.9-experimental"))
    }

    @Test
    fun unknownEmbeddingModelInfersEmbeddingViaPattern() {
        val caps = OpenAIModelRegistry.capabilitiesFor("text-embedding-4-huge")
        assertEquals(ModelType.EMBEDDING, caps.modelType)
    }

    @Test
    fun unknownModerationModelInfersModerationViaPattern() {
        val caps = OpenAIModelRegistry.capabilitiesFor("text-moderation-2099")
        assertEquals(ModelType.MODERATION, caps.modelType)
    }

    @Test
    fun completelyUnknownModelFallsBackToChat() {
        val caps = OpenAIModelRegistry.capabilitiesFor("totally-made-up-model")
        assertEquals(ModelType.CHAT, caps.modelType)
        assertTrue(caps.supportsTools)
    }

    @Test
    fun capabilitiesForNeverThrowsForUnknownModels() {
        // Exercises every fallback branch; the registry must degrade gracefully.
        for (model in listOf("", "o9-mega", "gpt-4.7-turbo", "chatgpt-future", "weird")) {
            OpenAIModelRegistry.capabilitiesFor(model)
        }
    }

    // -- Catalog completeness -----------------------------------------------

    @Test
    fun registeredModelsCoversAllFamilies() {
        val registered = OpenAIModelRegistry.registeredModels().toSet()
        val expected = listOf(
            "o1", "o3", "o4-mini",
            "gpt-5", "gpt-5.1", "gpt-5.2", "gpt-5.4", "gpt-5.5",
            "gpt-4", "gpt-4o", "gpt-4.1",
            "gpt-3.5-turbo",
            "text-embedding-3-large",
            "babbage-002", "codex-mini-latest",
        )
        for (model in expected) {
            assertTrue(model in registered, "$model should be in the catalog")
        }
    }
}
