package com.mojentic.omlx

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val DATA_PREFIX = "data:"
private const val KEEPALIVE_MODEL = "keepalive"

/**
 * True for an oMLX keep-alive frame: a `data:` line whose JSON `model` is
 * exactly `keepalive`.
 *
 * oMLX opens every chat stream with one and sends more during long prefill.
 * They carry no content, but the shared OpenAI stream parsers would record
 * `keepalive` as the provider model, so the gateway drops them first. The
 * gateway reads the body line by line, and the channel carries a partial line
 * across body chunks. The `: keep-alive` SSE comment form is not a `data:`
 * line, and the parsers already skip it.
 */
internal fun isKeepAliveFrame(line: String, json: Json): Boolean {
    if (!line.startsWith(DATA_PREFIX) || KEEPALIVE_MODEL !in line) return false
    val frame = runCatching { json.parseToJsonElement(line.removePrefix(DATA_PREFIX).trim()) }.getOrNull() as? JsonObject
    val model = frame?.get("model") as? JsonPrimitive
    return model?.isString == true && model.contentOrNull == KEEPALIVE_MODEL
}
