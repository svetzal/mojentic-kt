package com.mojentic.openai

/**
 * Static metadata about OpenAI chat models.
 *
 * Mirrors the Python reference's `OpenAIModelRegistry` — a small lookup table
 * for the context-window and feature flags a broker / chat session might
 * consult before issuing a call. Update conservatively: stale entries are far
 * better than wrong ones.
 */
public data class OpenAIModelInfo(
    val id: String,
    val contextWindow: Int,
    val supportsTools: Boolean,
    val supportsVision: Boolean,
    val supportsReasoningEffort: Boolean,
)

public object OpenAIModelRegistry {
    private fun entry(
        id: String,
        contextWindow: Int,
        tools: Boolean,
        vision: Boolean,
        reasoning: Boolean,
    ): OpenAIModelInfo = OpenAIModelInfo(
        id = id,
        contextWindow = contextWindow,
        supportsTools = tools,
        supportsVision = vision,
        supportsReasoningEffort = reasoning,
    )

    private val entries: Map<String, OpenAIModelInfo> = listOf(
        entry("gpt-4o", contextWindow = 128_000, tools = true, vision = true, reasoning = false),
        entry("gpt-4o-mini", contextWindow = 128_000, tools = true, vision = true, reasoning = false),
        entry("gpt-4.1", contextWindow = 1_000_000, tools = true, vision = true, reasoning = false),
        entry("gpt-4.1-mini", contextWindow = 1_000_000, tools = true, vision = true, reasoning = false),
        entry("o1", contextWindow = 200_000, tools = false, vision = false, reasoning = true),
        entry("o1-mini", contextWindow = 128_000, tools = false, vision = false, reasoning = true),
        entry("o3", contextWindow = 200_000, tools = true, vision = true, reasoning = true),
        entry("o3-mini", contextWindow = 200_000, tools = true, vision = false, reasoning = true),
    ).associateBy { it.id }

    public fun info(model: String): OpenAIModelInfo? = entries[model]

    public fun supportsTools(model: String): Boolean = info(model)?.supportsTools ?: true

    public fun supportsVision(model: String): Boolean = info(model)?.supportsVision ?: false

    public fun supportsReasoningEffort(model: String): Boolean = info(model)?.supportsReasoningEffort ?: false
}
