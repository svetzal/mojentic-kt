package com.mojentic.openai

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.EncodingType
import com.knuddels.jtokkit.api.IntArrayList
import com.mojentic.llm.TokenizerGateway

/**
 * JVM-only [TokenizerGateway] backed by jtokkit.
 *
 * Resolves the OpenAI encoding for a given model name via jtokkit's
 * registry; falls back to the `o200k_base` (GPT-4o family) encoding when
 * the model isn't recognised.
 */
public class JtokkitTokenizerGateway(model: String) : TokenizerGateway {
    private val encoding: Encoding = resolveEncoding(model)

    override fun encode(text: String): List<Int> {
        val ints = encoding.encode(text)
        return List(ints.size()) { i -> ints.get(i) }
    }

    override fun decode(tokens: List<Int>): String {
        val ints = IntArrayList(tokens.size)
        for (t in tokens) ints.add(t)
        return encoding.decode(ints)
    }

    override fun countTokens(text: String): Int = encoding.countTokens(text)

    public companion object {
        private val registry: EncodingRegistry by lazy { Encodings.newDefaultEncodingRegistry() }

        private fun resolveEncoding(model: String): Encoding {
            val byName = registry.getEncodingForModel(model)
            if (byName.isPresent) return byName.get()
            return registry.getEncoding(EncodingType.O200K_BASE)
        }
    }
}
