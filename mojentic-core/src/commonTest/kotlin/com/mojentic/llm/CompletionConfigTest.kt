package com.mojentic.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompletionConfigTest {
    @Test
    fun samplingAndLimitsDefaultToUnset() {
        val config = CompletionConfig()

        assertNull(config.temperature)
        assertNull(config.numCtx)
        assertNull(config.maxTokens)
        assertEquals(CompletionConfig.DEFAULT_MAX_TOOL_ITERATIONS, config.maxToolIterations)
        assertNull(config.reasoningEffort)
    }

    @Test
    fun copyDecrementsMaxToolIterations() {
        val config = CompletionConfig(maxToolIterations = 5)

        val decremented = config.copy(maxToolIterations = config.maxToolIterations?.minus(1))

        assertEquals(4, decremented.maxToolIterations)
    }

    @Test
    fun depthCopyPreservesRecoveryReasoningRetentionAndUnsetSampling() {
        val policy = com.mojentic.llm.recovery.RecoveryPolicy()
        val profile = ReasoningProfile.qwen38()
        val config = CompletionConfig(
            recovery = policy,
            reasoningProfile = profile,
            preserveReasoning = true,
            reasoningEffort = ReasoningEffort.XHIGH,
            responseFormat = ResponseFormat.Json(),
        )
        val copied = config.copy(maxToolIterations = 9)
        assertEquals(config, copied.copy(maxToolIterations = 10))
        assertEquals(policy, copied.recovery)
        assertEquals(profile, copied.reasoningProfile)
        assertEquals(true, copied.preserveReasoning)
        assertNull(copied.temperature)
        assertNull(copied.numCtx)
        assertNull(copied.maxTokens)
    }

    @Test
    fun reasoningEffortWireValuesAreLowercase() {
        assertEquals("low", ReasoningEffort.LOW.wireValue)
        assertEquals("medium", ReasoningEffort.MEDIUM.wireValue)
        assertEquals("high", ReasoningEffort.HIGH.wireValue)
    }
}
