package com.mojentic.llm

import com.mojentic.llm.tools.LlmTool
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * Provider-agnostic LLM gateway.
 *
 * One interface per provider implementation lives in its own Gradle module
 * (e.g. `mojentic-ollama`). Gateways are thin Ktor-Client wrappers — they
 * translate between provider HTTP APIs and Mojentic's neutral types. No
 * business logic lives in a gateway.
 *
 * All methods are `suspend` or return a `Flow`. Cancellation of the calling
 * coroutine propagates to the underlying HTTP request.
 */
public interface LlmGateway {
    /**
     * Single-shot completion.
     *
     * @param model Provider-side model identifier (e.g. `qwen2.5:7b` for Ollama).
     * @param messages Conversation history.
     * @param tools Tools to expose to the LLM. Empty / null means no tools.
     * @param config Shared completion knobs.
     * @return Aggregated gateway response. The broker — not the gateway —
     *         decides what to do with tool calls.
     */
    public suspend fun complete(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>? = null,
        config: CompletionConfig = CompletionConfig(),
    ): LlmGatewayResponse

    /**
     * Structured-output completion. The provider is instructed to return a
     * JSON object matching [schema].
     *
     * @return The parsed JSON element. The broker / `completeJson<T>` deserialises into `T`.
     */
    public suspend fun completeJson(
        model: String,
        messages: List<LlmMessage>,
        schema: JsonObject,
        config: CompletionConfig = CompletionConfig(),
    ): JsonObject

    /**
     * Streaming completion. Returns a cold `Flow` that emits one
     * [GatewayStreamEvent] per chunk. Cancellation tears down the HTTP request.
     */
    public fun stream(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>? = null,
        config: CompletionConfig = CompletionConfig(),
    ): Flow<GatewayStreamEvent>

    /**
     * List models the provider currently advertises.
     *
     * Some providers (e.g. OpenAI) only return models the API key is licensed
     * for; others (Ollama) return the locally pulled set.
     */
    public suspend fun availableModels(): List<String>
}
