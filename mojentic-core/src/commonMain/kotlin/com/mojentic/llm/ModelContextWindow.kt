package com.mojentic.llm

/**
 * Provider-advertised context capacity and the field that supplied it.
 * @property maxContextTokens Positive advertised token capacity.
 * @property field Provider field path, or `registry` for an exact catalog entry.
 * @property modelNumCtx Optional Ollama model parameter; distinct from capacity.
 */
public data class ModelContextWindow(
    val maxContextTokens: Int,
    val field: String,
    val modelNumCtx: Int? = null,
)
