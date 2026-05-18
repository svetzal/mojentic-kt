package com.mojentic.llm

/**
 * Reasoning effort hint for models that support extended thinking.
 *
 * Provider-specific behaviour mirrors the Python reference:
 * - Ollama maps any non-null value to `think: true`.
 * - OpenAI maps to the `reasoning_effort` API parameter for reasoning models.
 * - Anthropic maps to extended-thinking budget tiers.
 *
 * Default is `null` (no extended reasoning).
 */
public enum class ReasoningEffort {
    LOW,
    MEDIUM,
    HIGH,
    ;

    /** Lowercase string form used by provider APIs. */
    public val wireValue: String
        get() = name.lowercase()
}
