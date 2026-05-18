package com.mojentic.examples

import com.mojentic.openai.OpenAIEmbeddingsGateway
import kotlinx.coroutines.runBlocking
import kotlin.math.sqrt

/**
 * OpenAI embeddings demo. Embeds three short documents and prints the
 * pairwise cosine similarity. Requires `OPENAI_API_KEY`.
 */
fun main(): Unit = runBlocking {
    val apiKey = System.getenv("OPENAI_API_KEY")
        ?: error("Set OPENAI_API_KEY to run this example.")
    val model = System.getenv("MOJENTIC_EMBED_MODEL") ?: "text-embedding-3-small"

    val docs = listOf(
        "The quick brown fox jumps over the lazy dog.",
        "A swift russet vulpine leaps above an idle hound.",
        "Kotlin coroutines compose well with cold flows.",
    )

    val gateway = OpenAIEmbeddingsGateway(apiKey = apiKey)
    try {
        val vectors = gateway.embedBatch(model, docs)
        println("Cosine similarities (1.0 = identical):")
        for (i in vectors.indices) {
            for (j in i + 1 until vectors.size) {
                val s = cosine(vectors[i], vectors[j])
                println("  doc[$i] vs doc[$j] = ${"%.4f".format(s)}")
            }
        }
    } finally {
        gateway.close()
    }
}

private fun cosine(a: FloatArray, b: FloatArray): Double {
    require(a.size == b.size) { "vector size mismatch (${a.size} vs ${b.size})" }
    var dot = 0.0
    var na = 0.0
    var nb = 0.0
    for (i in a.indices) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    return dot / (sqrt(na) * sqrt(nb))
}
