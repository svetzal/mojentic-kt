package com.mojentic.realtime.internal

import com.mojentic.llm.LlmToolCall
import com.mojentic.realtime.RealtimeEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Translates raw OpenAI Realtime wire events into the vendor-neutral
 * [RealtimeEvent] union.
 *
 * The normaliser is stateful **only** with respect to in-progress function
 * calls: arguments arrive across multiple delta events and must be reassembled
 * before dispatch. Other state (turn IDs, session state) is read off the wire
 * directly. Mutable state is confined to a single `MutableMap` keyed by
 * `call_id`, accessed only from the normaliser's owning coroutine.
 *
 * Returns a list because a single wire event may produce zero, one, or
 * occasionally more vendor-neutral events.
 */
internal class RealtimeEventNormalizer(
    private val parser: Json = Json { ignoreUnknownKeys = true },
) {

    /** Accumulator for in-progress function-call argument streams, keyed by `call_id`. */
    private val pendingArgs: MutableMap<String, StringBuilder> = mutableMapOf()

    /** Tool calls whose argument stream has completed, awaiting dispatch on `response.done`. */
    private val readyToolCalls: MutableMap<String, LlmToolCall> = mutableMapOf()

    /** Track which call_ids belong to the current in-flight assistant turn. */
    private val currentTurnCallIds: MutableList<String> = mutableListOf()

    /** Translate a single raw wire event into zero or more vendor-neutral events. */
    fun translate(raw: JsonObject): List<RealtimeEvent> {
        val type = raw["type"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
        return sessionEvent(type, raw)
            ?: userTurnEvent(type, raw)
            ?: assistantTurnEvent(type, raw)
            ?: toolCallEvent(type, raw)
            ?: controlEvent(type, raw)
            ?: emptyList()
    }

    private fun sessionEvent(type: String, raw: JsonObject): List<RealtimeEvent>? = when (type) {
        OpenAiEventNames.SESSION_CREATED -> {
            val id = raw["session"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull.orEmpty()
            listOf(RealtimeEvent.SessionOpened(id))
        }
        OpenAiEventNames.SESSION_UPDATED -> {
            val instructions = raw["session"]?.jsonObject?.get("instructions")?.jsonPrimitive?.contentOrNull
            listOf(RealtimeEvent.SessionUpdated(instructions))
        }
        else -> null
    }

    private fun userTurnEvent(type: String, raw: JsonObject): List<RealtimeEvent>? = when (type) {
        OpenAiEventNames.SPEECH_STARTED ->
            listOf(RealtimeEvent.UserSpeechStarted(raw["item_id"]?.jsonPrimitive?.contentOrNull))
        OpenAiEventNames.SPEECH_STOPPED ->
            listOf(RealtimeEvent.UserSpeechStopped(raw["item_id"]?.jsonPrimitive?.contentOrNull))
        OpenAiEventNames.USER_TRANSCRIPT_DELTA -> {
            val itemId = raw["item_id"]?.jsonPrimitive?.contentOrNull
            val delta = raw["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (itemId == null) emptyList() else listOf(RealtimeEvent.UserTranscriptDelta(itemId, delta))
        }
        OpenAiEventNames.USER_TRANSCRIPT_COMPLETED -> {
            val itemId = raw["item_id"]?.jsonPrimitive?.contentOrNull
            val text = raw["transcript"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (itemId == null) emptyList() else listOf(RealtimeEvent.UserTranscript(itemId, text))
        }
        else -> null
    }

    private fun assistantTurnEvent(type: String, raw: JsonObject): List<RealtimeEvent>? {
        val turnId = raw["response_id"]?.jsonPrimitive?.contentOrNull
            ?: raw["response"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            ?: ""
        return when (type) {
            OpenAiEventNames.RESPONSE_CREATED -> {
                currentTurnCallIds.clear()
                listOf(RealtimeEvent.AssistantTurnStarted(turnId))
            }
            OpenAiEventNames.RESPONSE_TEXT_DELTA ->
                listOf(RealtimeEvent.AssistantTextDelta(turnId, raw["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()))
            OpenAiEventNames.RESPONSE_TEXT_DONE ->
                listOf(RealtimeEvent.AssistantText(turnId, raw["text"]?.jsonPrimitive?.contentOrNull.orEmpty()))
            OpenAiEventNames.RESPONSE_AUDIO_TRANSCRIPT_DELTA ->
                listOf(
                    RealtimeEvent.AssistantTranscriptDelta(turnId, raw["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()),
                )
            OpenAiEventNames.RESPONSE_AUDIO_TRANSCRIPT_DONE ->
                listOf(
                    RealtimeEvent.AssistantTranscript(turnId, raw["transcript"]?.jsonPrimitive?.contentOrNull.orEmpty()),
                )
            OpenAiEventNames.RESPONSE_AUDIO_DELTA -> {
                val base64 = raw["delta"]?.jsonPrimitive?.contentOrNull
                if (base64 == null) {
                    emptyList()
                } else {
                    listOf(RealtimeEvent.AssistantAudioDelta(turnId, Pcm16AudioCodec.decode(base64)))
                }
            }
            OpenAiEventNames.RESPONSE_DONE -> listOf(buildTurnCompleted(raw, turnId))
            else -> null
        }
    }

    private fun buildTurnCompleted(raw: JsonObject, turnId: String): RealtimeEvent.AssistantTurnCompleted {
        val usage = raw["response"]?.jsonObject?.get("usage")?.jsonObject
        return RealtimeEvent.AssistantTurnCompleted(
            turnId = turnId,
            inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
            outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
            totalTokens = usage?.get("total_tokens")?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
        )
    }

    private fun toolCallEvent(type: String, raw: JsonObject): List<RealtimeEvent>? = when (type) {
        OpenAiEventNames.RESPONSE_OUTPUT_ITEM_ADDED -> {
            val item = raw["item"]?.jsonObject
            if (item == null || item["type"]?.jsonPrimitive?.contentOrNull != "function_call") {
                emptyList()
            } else {
                val callId = item["call_id"]?.jsonPrimitive?.contentOrNull
                val name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val turnId = raw["response_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (callId == null) {
                    emptyList()
                } else {
                    pendingArgs[callId] = StringBuilder()
                    currentTurnCallIds += callId
                    listOf(RealtimeEvent.ToolCallStarted(turnId = turnId, callId = callId, name = name))
                }
            }
        }
        OpenAiEventNames.RESPONSE_FUNCTION_ARGS_DELTA -> {
            val callId = raw["call_id"]?.jsonPrimitive?.contentOrNull
            val delta = raw["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (callId == null) {
                emptyList()
            } else {
                pendingArgs.getOrPut(callId) { StringBuilder() }.append(delta)
                listOf(RealtimeEvent.ToolCallArgsDelta(callId, delta))
            }
        }
        OpenAiEventNames.RESPONSE_FUNCTION_ARGS_DONE -> {
            val callId = raw["call_id"]?.jsonPrimitive?.contentOrNull
            if (callId == null) {
                emptyList()
            } else {
                listOf(finaliseToolCall(callId, raw))
            }
        }
        OpenAiEventNames.RESPONSE_OUTPUT_ITEM_DONE -> emptyList()
        else -> null
    }

    private fun finaliseToolCall(callId: String, raw: JsonObject): RealtimeEvent.ToolCallDispatched {
        val name = raw["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val argsText = raw["arguments"]?.jsonPrimitive?.contentOrNull
            ?: pendingArgs[callId]?.toString().orEmpty()
        pendingArgs.remove(callId)
        val argsObject = parseObjectOrEmpty(argsText)
        val toolCall = LlmToolCall(id = callId, name = name, arguments = argsObject)
        readyToolCalls[callId] = toolCall
        return RealtimeEvent.ToolCallDispatched(callId, toolCall)
    }

    private fun controlEvent(type: String, raw: JsonObject): List<RealtimeEvent>? = when (type) {
        OpenAiEventNames.RATE_LIMITS_UPDATED -> {
            val resetMs = raw["rate_limits"]?.jsonObject?.get("reset_seconds")
                ?.jsonPrimitive?.longOrNull?.let { it * MILLIS_PER_SECOND }
            listOf(RealtimeEvent.RateLimited(resetMs, raw))
        }
        OpenAiEventNames.ERROR -> {
            val message = raw["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                ?: "Unknown realtime gateway error"
            listOf(RealtimeEvent.GatewayError(message, recoverable = false))
        }
        else -> null
    }

    /** Drain the set of call_ids that completed during the current assistant turn. */
    fun drainTurnCallIds(): List<String> {
        val snapshot = currentTurnCallIds.toList()
        currentTurnCallIds.clear()
        return snapshot
    }

    /** Retrieve and remove a ready tool call by id (returns null if not ready). */
    fun consumeReadyToolCall(callId: String): LlmToolCall? = readyToolCalls.remove(callId)

    /** All tool calls ready for dispatch, in the order they completed. */
    fun consumeAllReady(): List<LlmToolCall> {
        val list = readyToolCalls.values.toList()
        readyToolCalls.clear()
        return list
    }

    private fun parseObjectOrEmpty(text: String): JsonObject = try {
        if (text.isBlank()) JsonObject(emptyMap()) else parser.parseToJsonElement(text).jsonObject
    } catch (_: Throwable) {
        JsonObject(emptyMap())
    }

    private companion object {
        private const val MILLIS_PER_SECOND: Long = 1_000L
    }
}
