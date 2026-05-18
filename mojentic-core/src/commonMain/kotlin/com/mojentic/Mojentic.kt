package com.mojentic

/**
 * Mojentic — Kotlin Multiplatform LLM integration framework.
 *
 * See `mojentic-unify/KOTLIN.md` for the roadmap. Phase 1 shipped the core
 * [com.mojentic.llm.LlmBroker] surface plus the Ollama gateway. Phase 2
 * adds [com.mojentic.llm.ChatSession], the `mojentic-openai` gateway
 * module, tokenizer and embeddings gateways, and multimodal message
 * support.
 */
public object Mojentic {
    public const val VERSION: String = "0.2.0-SNAPSHOT"
}
