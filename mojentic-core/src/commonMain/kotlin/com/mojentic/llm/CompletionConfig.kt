package com.mojentic.llm

/**
 * Configuration for LLM completion requests.
 *
 * Provides a unified, immutable configuration value across providers. Mirrors
 * `mojentic.llm.completion_config.CompletionConfig` in the Python reference.
 *
 * @property recovery Opt-in ordinary/structured/streaming recovery for OpenAI, Anthropic, Ollama and oMLX;
 * null preserves legacy behavior. [com.mojentic.llm.recovery.RecoveryPolicy] validates at construction,
 * throwing [com.mojentic.errors.InvalidRecoveryPolicyException] before a completion can start.
 * @property temperature Sampling temperature. Higher values produce more random output.
 * @property numCtx Context window size in tokens.
 * @property maxTokens Maximum tokens to generate in the response.
 * @property numPredict Tokens to predict, `-1` for no limit. Ollama-specific knob.
 * @property reasoningEffort Optional native control; null leaves provider defaults unknown.
 * @property reasoningProfile Caller-selected capability contract, never inferred from a model name.
 * @property preserveReasoning Opt-in retention of completed native assistant reasoning in tool history.
 * @property maxToolIterations Optional ceiling on broker tool-recursion depth; null is unlimited.
 * @property responseFormat Optional output format forwarded by gateways in streaming and
 *           non-streaming requests. Null keeps the provider default. See [ResponseFormat].
 */
public data class CompletionConfig(
    val temperature: Double = DEFAULT_TEMPERATURE,
    val numCtx: Int = DEFAULT_NUM_CTX,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val numPredict: Int = DEFAULT_NUM_PREDICT,
    val reasoningEffort: ReasoningEffort? = null,
    val maxToolIterations: Int? = DEFAULT_MAX_TOOL_ITERATIONS,
    val responseFormat: ResponseFormat? = null,
    val recovery: com.mojentic.llm.recovery.RecoveryPolicy? = null,
    val reasoningProfile: ReasoningProfile? = null,
    val preserveReasoning: Boolean = false,
) {
    /** Retains the pre-recovery constructor ABI and its default-argument bridge. */
    public constructor(
        temperature: Double = DEFAULT_TEMPERATURE,
        numCtx: Int = DEFAULT_NUM_CTX,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        numPredict: Int = DEFAULT_NUM_PREDICT,
        reasoningEffort: ReasoningEffort? = null,
        maxToolIterations: Int? = DEFAULT_MAX_TOOL_ITERATIONS,
        responseFormat: ResponseFormat? = null,
    ) : this(temperature, numCtx, maxTokens, numPredict, reasoningEffort, maxToolIterations, responseFormat, null)

    /** Retains the pre-recovery copy ABI; broker depth updates preserve the recovery policy. */
    public fun copy(
        temperature: Double = this.temperature,
        numCtx: Int = this.numCtx,
        maxTokens: Int = this.maxTokens,
        numPredict: Int = this.numPredict,
        reasoningEffort: ReasoningEffort? = this.reasoningEffort,
        maxToolIterations: Int? = this.maxToolIterations,
        responseFormat: ResponseFormat? = this.responseFormat,
    ): CompletionConfig = CompletionConfig(
        temperature,
        numCtx,
        maxTokens,
        numPredict,
        reasoningEffort,
        maxToolIterations,
        responseFormat,
        recovery,
        reasoningProfile,
        preserveReasoning,
    )

    public companion object {
        public const val DEFAULT_TEMPERATURE: Double = 1.0
        public const val DEFAULT_NUM_CTX: Int = 32_768
        public const val DEFAULT_MAX_TOKENS: Int = 16_384
        public const val DEFAULT_NUM_PREDICT: Int = -1
        public const val DEFAULT_MAX_TOOL_ITERATIONS: Int = 10
    }
}
