package com.mojentic.llm

import com.mojentic.errors.LlmGatewayException

/**
 * Caller-selected model capabilities; selection never depends on model-name fragments.
 * @property name Stable label for diagnostics and provenance.
 * @property supportedEfforts Exact accepted controls; High and XHigh are distinct.
 * @property supportsReasoningHistory Whether native assistant reasoning is accepted in history.
 */
public data class ReasoningProfile(
    val name: String,
    val supportedEfforts: Set<ReasoningEffort>,
    val supportsReasoningHistory: Boolean,
) {
    /**
     * Reject unsupported explicit controls before HTTP.
     * @throws LlmGatewayException On unsupported effort or native history.
     */
    public fun validate(effort: ReasoningEffort?, preserveHistory: Boolean) {
        if (effort != null && effort !in supportedEfforts) {
            throw LlmGatewayException("Reasoning effort $effort is unsupported by profile $name")
        }
        if (preserveHistory && !supportsReasoningHistory) {
            throw LlmGatewayException("Profile $name does not support native reasoning history")
        }
    }

    public companion object {
        /** Qwen3.8's exact supported set, without imposing a default effort. */
        public fun qwen38(): ReasoningProfile = ReasoningProfile(
            "qwen3.8",
            setOf(ReasoningEffort.DISABLED, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.XHIGH),
            true,
        )
    }
}

/**
 * Validate caller-selected capabilities before starting a completion.
 * @param nativeHistoryPresent Whether the caller supplied native reasoning in history.
 * @throws LlmGatewayException If the profile rejects effort or history.
 */
public fun CompletionConfig.validateReasoning(nativeHistoryPresent: Boolean = false) {
    reasoningProfile?.validate(reasoningEffort, preserveReasoning || nativeHistoryPresent)
}

/**
 * Reject new controls on providers whose legacy behavior is intentionally unchanged.
 * @throws LlmGatewayException If the profile or provider does not support the controls.
 */
public fun CompletionConfig.rejectNewReasoningControls(provider: String) {
    validateReasoning()
    if (preserveReasoning || reasoningEffort in setOf(
            ReasoningEffort.DISABLED,
            ReasoningEffort.ENABLED,
            ReasoningEffort.XHIGH,
        )
    ) {
        throw LlmGatewayException("$provider does not support the requested native reasoning controls")
    }
}
