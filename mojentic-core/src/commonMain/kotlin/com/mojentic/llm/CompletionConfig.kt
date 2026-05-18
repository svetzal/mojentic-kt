package com.mojentic.llm

/**
 * Configuration for LLM completion requests.
 *
 * Provides a unified, immutable configuration value across providers. Mirrors
 * `mojentic.llm.completion_config.CompletionConfig` in the Python reference.
 *
 * @property temperature Sampling temperature. Higher values produce more random output.
 * @property numCtx Context window size in tokens.
 * @property maxTokens Maximum tokens to generate in the response.
 * @property numPredict Tokens to predict, `-1` for no limit. Ollama-specific knob.
 * @property reasoningEffort Optional reasoning effort for thinking-capable models.
 * @property maxToolIterations Hard ceiling on broker tool-recursion depth.
 */
public data class CompletionConfig(
    val temperature: Double = DEFAULT_TEMPERATURE,
    val numCtx: Int = DEFAULT_NUM_CTX,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val numPredict: Int = DEFAULT_NUM_PREDICT,
    val reasoningEffort: ReasoningEffort? = null,
    val maxToolIterations: Int = DEFAULT_MAX_TOOL_ITERATIONS,
) {
    public companion object {
        public const val DEFAULT_TEMPERATURE: Double = 1.0
        public const val DEFAULT_NUM_CTX: Int = 32_768
        public const val DEFAULT_MAX_TOKENS: Int = 16_384
        public const val DEFAULT_NUM_PREDICT: Int = -1
        public const val DEFAULT_MAX_TOOL_ITERATIONS: Int = 10
    }
}
