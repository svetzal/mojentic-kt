package com.mojentic.anthropic

/**
 * Exact IDs/aliases matching the Python registry; unknown models use section 3.4's fallback.
 * Output maxima: https://platform.claude.com/docs/en/models/sonnet-4-5/overview and
 * https://platform.claude.com/docs/en/models/haiku-4-5/overview (registry aligned with Python on 2026-10-17).
 */
internal object AnthropicOutputLimits {
    private const val SONNET_HAIKU_45_MAX_OUTPUT = 64_000

    private val registered = mapOf(
        "claude-sonnet-4-5" to SONNET_HAIKU_45_MAX_OUTPUT,
        "claude-sonnet-4-5-20250929" to SONNET_HAIKU_45_MAX_OUTPUT,
        "claude-haiku-4-5" to SONNET_HAIKU_45_MAX_OUTPUT,
        "claude-haiku-4-5-20251001" to SONNET_HAIKU_45_MAX_OUTPUT,
    )

    fun forModel(model: String): Int = registered[model] ?: AnthropicGateway.DEFAULT_MAX_TOKENS
}
