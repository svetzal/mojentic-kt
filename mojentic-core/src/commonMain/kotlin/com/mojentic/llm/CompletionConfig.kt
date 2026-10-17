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
 * @property temperature Sampling temperature; null leaves the provider default. Explicit zero is preserved.
 * @property numCtx Ollama context window size in tokens; null leaves model/server settings.
 * @property maxTokens Only output limit; null leaves provider defaults except Anthropic’s required registry fallback.
 * @property reasoningEffort Optional native control; null leaves provider defaults unknown.
 * @property reasoningProfile Caller-selected capability contract, never inferred from a model name.
 * @property preserveReasoning Opt-in retention of completed native assistant reasoning in tool history.
 * @property maxToolIterations Optional ceiling on broker tool-recursion depth; null is unlimited.
 * @property responseFormat Optional output format forwarded by gateways in streaming and
 *           non-streaming requests. Null keeps the provider default. See [ResponseFormat].
 */
public data class CompletionConfig(
    val temperature: Double? = null,
    val numCtx: Int? = null,
    val maxTokens: Int? = null,
    val reasoningEffort: ReasoningEffort? = null,
    val maxToolIterations: Int? = DEFAULT_MAX_TOOL_ITERATIONS,
    val responseFormat: ResponseFormat? = null,
    val recovery: com.mojentic.llm.recovery.RecoveryPolicy? = null,
    val reasoningProfile: ReasoningProfile? = null,
    val preserveReasoning: Boolean = false,
) {
    public companion object {
        public const val DEFAULT_MAX_TOOL_ITERATIONS: Int = 10
    }
}
