package com.mojentic.llm

import kotlinx.serialization.json.JsonObject

/**
 * Output format requested from the provider through
 * [CompletionConfig.responseFormat].
 *
 * Gateways forward the format in streaming and non-streaming requests. The
 * request only records what was asked for. It does not prove that the
 * provider enforced it, so callers must still validate the returned content.
 *
 * A `null` format leaves the request unchanged and uses the provider default.
 */
public sealed interface ResponseFormat {
    /** Plain text. OpenAI receives `{"type":"text"}`; Ollama receives no `format`. */
    public data object Text : ResponseFormat

    /**
     * A JSON object.
     *
     * With no [schema], OpenAI receives JSON object mode and Ollama receives
     * `format: "json"`. With a [schema], OpenAI receives JSON schema mode and
     * Ollama receives the schema as `format`.
     *
     * @property schema Optional JSON schema that the object must match.
     */
    public data class Json(val schema: JsonObject? = null) : ResponseFormat
}
