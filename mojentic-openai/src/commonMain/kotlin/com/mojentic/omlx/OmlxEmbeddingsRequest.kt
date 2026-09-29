package com.mojentic.omlx

import kotlinx.serialization.Serializable

/** `POST /v1/embeddings` body. oMLX takes one text per request, with no client-side chunking. */
@Serializable
internal data class OmlxEmbeddingsRequest(
    val model: String,
    val input: String,
)
