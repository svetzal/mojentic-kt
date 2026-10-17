package com.mojentic.omlx

import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.ModelContextWindow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

internal fun parseOmlxContextWindow(body: String, model: String): ModelContextWindow? {
    val entry = modelEntries(body).firstOrNull { (it["id"] as? JsonPrimitive)?.content == model }
    return if (entry == null || "max_model_len" !in entry) null else advertisedWindow(entry)
}

private fun modelEntries(body: String): List<JsonObject> {
    val root = Json.parseToJsonElement(body) as? JsonObject
        ?: throw LlmGatewayException("Invalid oMLX metadata object")
    val models = root["data"] as? JsonArray ?: throw LlmGatewayException("Invalid oMLX metadata data")
    return models.map { modelEntry(it) }
}

private fun modelEntry(value: kotlinx.serialization.json.JsonElement): JsonObject {
    val item = value as? JsonObject ?: throw LlmGatewayException("Invalid oMLX model entry")
    val id = item["id"] as? JsonPrimitive
    if (id?.isString != true) throw LlmGatewayException("Invalid oMLX model id")
    return item
}

private fun advertisedWindow(entry: JsonObject): ModelContextWindow {
    val value = entry["max_model_len"] as? JsonPrimitive
    val tokens = value?.takeUnless { it.isString }?.intOrNull
    if (tokens == null || tokens <= 0) throw LlmGatewayException("Invalid oMLX max_model_len")
    return ModelContextWindow(tokens, "max_model_len")
}
