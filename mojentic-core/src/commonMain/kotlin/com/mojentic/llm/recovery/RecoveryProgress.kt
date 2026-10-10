package com.mojentic.llm.recovery

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Observe provider output before any user capture callback; never expose its text. */
internal fun observedProgress(status: Int?, bytes: ByteArray): RecoveryProgress {
    val success = status in 200..299
    val body = bytes.decodeToString()
    val message = if (success) responseMessage(body) else null
    val content = message?.hasText("content") ?: (success && CONTENT_PREFIX.containsMatchIn(body))
    val reasoning = message?.hasReasoning()
        ?: (success && REASONING_PREFIX.containsMatchIn(body))
    val calls = message?.get("tool_calls") as? JsonArray
    val tools = calls?.isNotEmpty() ?: (success && TOOL_PREFIX.containsMatchIn(body))
    return RecoveryProgress(
        headersReceived = status != null,
        rawBytes = bytes.size.toLong(),
        semanticObserved = content || reasoning || tools,
        contentObserved = content,
        reasoningObserved = reasoning,
        toolFragmentsObserved = tools,
        completedToolCallsObserved = calls?.size ?: 0,
        replayUnsafe = success && bytes.isNotEmpty(),
    )
}

private fun JsonObject.hasText(key: String): Boolean = (get(key) as? JsonPrimitive)?.let {
    it.isString && it.content.isNotEmpty()
} == true

// An incomplete JSON response cannot be decoded. These prefixes conservatively
// observe non-empty semantic fields; every partial successful body still vetoes replay.
private val CONTENT_PREFIX = Regex("\"content\"\\s*:\\s*\"[^\"]")
private val REASONING_PREFIX = Regex("\"(?:thinking|reasoning_content)\"\\s*:\\s*\"[^\"]")
private val TOOL_PREFIX = Regex("\"tool_calls\"\\s*:\\s*\\[\\s*\\{")

private fun responseMessage(body: String): JsonObject? {
    val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
    val choice = (root?.get("choices") as? JsonArray)?.firstOrNull() as? JsonObject
    return (root?.get("message") ?: choice?.get("message")) as? JsonObject
}

private fun JsonObject.hasReasoning(): Boolean = hasText("thinking") || hasText("reasoning_content")

/** Conservative semantic observation includes incomplete frames before capture and parsing. */
internal fun streamingProgress(bytes: ByteArray): RecoveryProgress {
    val body = bytes.decodeToString()
    val keys = decodedSemanticKeys(body)
    val content = CONTENT_PREFIX.containsMatchIn(keys)
    val reasoning = REASONING_PREFIX.containsMatchIn(keys)
    val tools = TOOL_PREFIX.containsMatchIn(keys)
    var counts = RecoverySemanticProgress()
    body.lineSequence().forEach { line ->
        val payload = line.removePrefix("data:").trim()
        val root = runCatching { Json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
        val choice = (root?.get("choices") as? JsonArray)?.firstOrNull() as? JsonObject
        val delta = (root?.get("message") ?: choice?.get("delta")) as? JsonObject
        fun size(key: String): Long = (delta?.get(key) as? JsonPrimitive)?.takeIf { it.isString }
            ?.content?.encodeToByteArray()?.size?.toLong() ?: 0
        val calls = (delta?.get("tool_calls") as? JsonArray)?.size?.toLong() ?: 0
        counts = counts.copy(
            contentBytes = counts.contentBytes + size("content"),
            reasoningBytes = counts.reasoningBytes + size("thinking") + size("reasoning_content"),
            toolFragments = counts.toolFragments + calls,
        )
    }
    return RecoveryProgress(
        true,
        bytes.size.toLong(),
        content || reasoning || tools || counts.contentBytes > 0 || counts.reasoningBytes > 0 || counts.toolFragments > 0,
        contentObserved = content || counts.contentBytes > 0,
        reasoningObserved = reasoning || counts.reasoningBytes > 0,
        toolFragmentsObserved = tools || counts.toolFragments > 0,
        observed = counts,
    )
}

// Match whole JSON string tokens so a quoted value cannot masquerade as a key.
// Only normalize recognized keys; leave values and original captured bytes untouched.
private val JSON_STRING = Regex("\"(?:\\\\.|[^\"\\\\])*\"")
private val SEMANTIC_KEYS = setOf("content", "thinking", "reasoning_content", "tool_calls")

private fun decodedSemanticKeys(body: String): String = JSON_STRING.replace(body) { match ->
    val isKey = body.substring(match.range.last + 1).dropWhile { it.isWhitespace() }.startsWith(':')
    val key = if (isKey) {
        runCatching { (Json.parseToJsonElement(match.value) as? JsonPrimitive)?.content }.getOrNull()
    } else {
        null
    }
    if (key in SEMANTIC_KEYS) "\"$key\"" else match.value
}
