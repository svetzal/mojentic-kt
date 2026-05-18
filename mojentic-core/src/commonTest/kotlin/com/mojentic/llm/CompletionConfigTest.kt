package com.mojentic.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompletionConfigTest {
    @Test
    fun defaultsMatchPythonReference() {
        val config = CompletionConfig()

        assertEquals(CompletionConfig.DEFAULT_TEMPERATURE, config.temperature)
        assertEquals(CompletionConfig.DEFAULT_NUM_CTX, config.numCtx)
        assertEquals(CompletionConfig.DEFAULT_MAX_TOKENS, config.maxTokens)
        assertEquals(CompletionConfig.DEFAULT_NUM_PREDICT, config.numPredict)
        assertEquals(CompletionConfig.DEFAULT_MAX_TOOL_ITERATIONS, config.maxToolIterations)
        assertNull(config.reasoningEffort)
    }

    @Test
    fun copyDecrementsMaxToolIterations() {
        val config = CompletionConfig(maxToolIterations = 5)

        val decremented = config.copy(maxToolIterations = config.maxToolIterations - 1)

        assertEquals(4, decremented.maxToolIterations)
    }

    @Test
    fun reasoningEffortWireValuesAreLowercase() {
        assertEquals("low", ReasoningEffort.LOW.wireValue)
        assertEquals("medium", ReasoningEffort.MEDIUM.wireValue)
        assertEquals("high", ReasoningEffort.HIGH.wireValue)
    }
}
