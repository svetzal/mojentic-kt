package com.mojentic.llm.recovery

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Conservative public metadata projection; exact evidence stays in ResponseEvidence. */
internal object RecoveryMetadata {
    private val codes = setOf(
        "rate_limit_exceeded",
        "server_error",
        "invalid_request_error",
        "invalid_api_key",
        "model_not_found",
        "insufficient_quota",
        "overloaded_error",
    )
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val compactUuid = Regex("[0-9a-fA-F]{32}")

    fun requestId(headers: Map<String, List<String>>, payload: String, apiKey: String?): String? {
        val value = headers.entries.filter { it.key.equals("x-request-id", ignoreCase = true) }
            .flatMap { it.value }.singleOrNull() ?: return null
        val unprefixed = value.removePrefix("req_")
        val valid = uuid.matches(unprefixed) || compactUuid.matches(unprefixed) ||
            (unprefixed.startsWith("urn:uuid:") && uuid.matches(unprefixed.removePrefix("urn:uuid:"))) ||
            (unprefixed.startsWith("{") && unprefixed.endsWith("}") && uuid.matches(unprefixed.substring(1, unprefixed.lastIndex)))
        if (!valid) return null
        val uuidText = unprefixed.removePrefix("urn:uuid:").removeSurrounding("{", "}")
        return value.takeUnless {
            echoes(it, payload, apiKey) || echoes(unprefixed, payload, apiKey) || echoes(uuidText, payload, apiKey)
        }
    }

    fun code(bytes: ByteArray, payload: String, apiKey: String?): String? {
        val body = bytes.decodeToString()
        val documents = listOf(body) + body.lineSequence().map { it.removePrefix("data:").trim() }.toList()
        val received = documents.mapNotNull { document ->
            val error = (parse(document) as? JsonObject)?.get("error") as? JsonObject
            (error?.get("code") as? JsonPrimitive)?.takeIf { it.isString }?.content
        }.distinct()
        return received.singleOrNull()?.takeIf { it in codes && !echoes(it, payload, apiKey) }
    }

    private fun echoes(value: String, payload: String, apiKey: String?): Boolean =
        payload.contains(value) || apiKey?.contains(value) == true || contains(parse(payload), value)

    private fun contains(element: JsonElement?, value: String): Boolean = when (element) {
        is JsonPrimitive -> element.isString && element.content.contains(value)
        is JsonObject -> element.any { (key, child) -> key.contains(value) || contains(child, value) }
        is JsonArray -> element.any { contains(it, value) }
        else -> false
    }

    private fun parse(value: String): JsonElement? = runCatching { Json.parseToJsonElement(value) }.getOrNull()
}
