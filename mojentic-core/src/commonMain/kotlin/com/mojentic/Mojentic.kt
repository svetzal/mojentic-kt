package com.mojentic

/**
 * Mojentic — Kotlin Multiplatform LLM integration framework.
 *
 * See `mojentic-unify/KOTLIN.md` for the roadmap. Phase 1 shipped the core
 * [com.mojentic.llm.LlmBroker] surface plus the Ollama gateway; Phase 2
 * added [com.mojentic.llm.ChatSession], the `mojentic-openai` gateway
 * module, tokenizer and embeddings gateways, and multimodal message
 * support. Phase 3 adds the full
 * [com.mojentic.tracer.TracerSystem] backed by
 * [com.mojentic.tracer.EventStore] plus
 * [com.mojentic.llm.tools.ParallelToolRunner] for opt-in concurrent
 * tool execution.
 */
public object Mojentic {
    public const val VERSION: String = "0.3.0-SNAPSHOT"
}
