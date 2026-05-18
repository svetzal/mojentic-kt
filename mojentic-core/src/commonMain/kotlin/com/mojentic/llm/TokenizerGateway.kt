package com.mojentic.llm

/**
 * Pluggable tokenizer for cost-aware context-window management.
 *
 * Tokenization is provider- and model-specific and there is no single
 * cross-platform Kotlin library that covers every encoding. The gateway
 * pattern lets callers supply whichever implementation suits their target:
 * the OpenAI port ships a JVM-only jtokkit-backed implementation; Kotlin/Native
 * consumers can plug in their own or skip token counting entirely.
 *
 * Implementations must be safe to call from any coroutine.
 */
public interface TokenizerGateway {
    /** Encode [text] into the underlying tokenizer's integer tokens. */
    public fun encode(text: String): List<Int>

    /** Inverse of [encode]. */
    public fun decode(tokens: List<Int>): String

    /**
     * Token count. Equivalent to `encode(text).size` but implementations are
     * encouraged to short-circuit when the underlying tokenizer offers a
     * cheaper count path.
     */
    public fun countTokens(text: String): Int = encode(text).size
}
