package com.mojentic.llm

/**
 * Vector embedding provider.
 *
 * Concrete implementations live in provider modules (`mojentic-openai`
 * ships `OpenAIEmbeddingsGateway`). Implementations are gateways in the
 * strict sense: thin HTTP wrappers around a provider endpoint, with no
 * business logic.
 */
public interface EmbeddingsGateway {
    /**
     * Embed a single document. Returns a dense float vector whose length is
     * the provider's embedding dimension for [model].
     */
    public suspend fun embed(model: String, text: String): FloatArray

    /**
     * Embed many documents in one call. Order of the output list matches
     * the order of [texts]. Implementations should batch when the provider
     * supports it.
     */
    public suspend fun embedBatch(model: String, texts: List<String>): List<FloatArray>
}
