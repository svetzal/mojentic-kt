package com.mojentic.ollama

import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.ModelContextWindow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

internal fun parseContextWindow(body: String): ModelContextWindow? {
    val root = Json.parseToJsonElement(body) as? JsonObject
        ?: throw LlmGatewayException("Invalid Ollama metadata object")
    val info = when (val value = root["model_info"]) {
        null, JsonNull -> null
        is JsonObject -> value
        else -> throw LlmGatewayException("Invalid Ollama model_info")
    }
    val key = info?.keys?.sorted()?.firstOrNull { it.endsWith(".context_length") }
    val capacity = key?.let { positiveInteger(info[it], "model_info.$it") }
    val modelNumCtx = parseModelNumCtx(root["parameters"])
    return if (key == null || capacity == null) null else ModelContextWindow(capacity, "model_info.$key", modelNumCtx)
}

private fun positiveInteger(value: kotlinx.serialization.json.JsonElement?, field: String): Int {
    val tokens = (value as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
    if (tokens == null || tokens <= 0) throw LlmGatewayException("Invalid Ollama $field")
    return tokens
}

private fun parseModelNumCtx(value: kotlinx.serialization.json.JsonElement?): Int? {
    if (value == null || value == JsonNull) return null
    val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: throw LlmGatewayException("Invalid Ollama parameters")
    // The shared v1 artifact includes literal escaped separators as well as ordinary text lines.
    val line = text.replace("\\n", "\n").replace("\\t", "\t").lineSequence()
        .map { it.trim().split(Regex("\\s+")) }.firstOrNull { it.first() == "num_ctx" } ?: return null
    val tokens = line.getOrNull(1)?.toIntOrNull()
    if (line.size != 2 || tokens == null || tokens <= 0) throw LlmGatewayException("Invalid Ollama num_ctx parameter")
    return tokens
}

/**
 * Computes the request's context upper bound without I/O or configuration changes.
 * Caller [numCtx] takes precedence over the model parameter, then the advertised capacity.
 * Null [numCtx] means the caller omitted it; without limits the server's actual default is unknown.
 * Unknown [window] returns null even when a caller limit is supplied.
 * @throws IllegalArgumentException If a supplied caller limit is not positive.
 */
public fun effectiveContextWindow(window: ModelContextWindow?, numCtx: Int? = null): Int? {
    require(numCtx == null || numCtx > 0) { "numCtx must be positive when supplied" }
    return window?.let { minOf(it.maxContextTokens, numCtx ?: it.modelNumCtx ?: it.maxContextTokens) }
}

/** Uses explicit [CompletionConfig.numCtx], or model settings when unset. */
public fun effectiveContextWindow(window: ModelContextWindow?, config: CompletionConfig): Int? =
    effectiveContextWindow(window, config.numCtx)
