package com.mojentic.realtime.openai.internal

import com.mojentic.realtime.AudioFrame
import com.mojentic.realtime.ClientRealtimeEvent
import com.mojentic.realtime.RealtimeVoiceConfig
import com.mojentic.realtime.ToolChoice
import com.mojentic.realtime.VadConfig
import com.mojentic.realtime.internal.Pcm16AudioCodec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Converts vendor-neutral [ClientRealtimeEvent]s into OpenAI Realtime wire JSON,
 * and decodes provider audio deltas back into [AudioFrame]s.
 *
 * This is the only place in the realtime stack that knows OpenAI's wire
 * vocabulary. The gateway is a thin transport; the broker speaks the
 * vendor-neutral types.
 */
internal object OpenAiEventCodec {

    /** Translate a typed client event into its OpenAI wire-format envelope. */
    fun clientEventToWire(event: ClientRealtimeEvent): JsonObject = when (event) {
        is ClientRealtimeEvent.SessionUpdate -> buildJsonObject {
            put("type", "session.update")
            put("session", sessionConfigJson(event.config))
        }
        is ClientRealtimeEvent.InputAudioBufferAppend -> buildJsonObject {
            put("type", "input_audio_buffer.append")
            put("audio", encodeAudio(event.frame))
        }
        ClientRealtimeEvent.InputAudioBufferCommit -> buildJsonObject {
            put("type", "input_audio_buffer.commit")
        }
        ClientRealtimeEvent.InputAudioBufferClear -> buildJsonObject {
            put("type", "input_audio_buffer.clear")
        }
        is ClientRealtimeEvent.UserText -> buildJsonObject {
            put("type", "conversation.item.create")
            put(
                "item",
                buildJsonObject {
                    put("type", "message")
                    put("role", "user")
                    put(
                        "content",
                        buildJsonArray {
                            addJsonObject {
                                put("type", "input_text")
                                put("text", event.text)
                            }
                        },
                    )
                },
            )
        }
        is ClientRealtimeEvent.FunctionCallOutput -> buildJsonObject {
            put("type", "conversation.item.create")
            put(
                "item",
                buildJsonObject {
                    put("type", "function_call_output")
                    put("call_id", event.callId)
                    put("output", event.output)
                },
            )
        }
        ClientRealtimeEvent.ResponseCreate -> buildJsonObject {
            put("type", "response.create")
        }
        ClientRealtimeEvent.ResponseCancel -> buildJsonObject {
            put("type", "response.cancel")
        }
    }

    /** Build the inner `session` object for `session.update`. */
    private fun sessionConfigJson(config: RealtimeVoiceConfig): JsonObject = buildJsonObject {
        put(
            "modalities",
            buildJsonArray { config.modalities.forEach { add(it.wireValue) } },
        )
        config.instructions?.let { put("instructions", it) }
        config.voice?.let { put("voice", it) }
        put("input_audio_format", config.audioFormat.wireValue)
        put("output_audio_format", config.audioFormat.wireValue)
        config.inputAudioTranscriptionModel?.let { model ->
            put(
                "input_audio_transcription",
                buildJsonObject { put("model", model) },
            )
        }
        put("turn_detection", turnDetectionJson(config.turnDetection))
        if (config.tools.isNotEmpty()) {
            put(
                "tools",
                buildJsonArray {
                    config.tools.forEach { tool ->
                        addJsonObject {
                            put("type", "function")
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.descriptor.parameters)
                        }
                    }
                },
            )
        }
        put("tool_choice", toolChoiceJson(config.toolChoice))
        config.temperature?.let { put("temperature", it) }
        config.maxResponseOutputTokens?.let { put("max_response_output_tokens", it) }
        config.providerExtras.forEach { (key, value) -> put(key, value) }
    }

    private fun turnDetectionJson(vad: VadConfig): JsonObject = when (vad) {
        is VadConfig.Server -> buildJsonObject {
            put("type", "server_vad")
            put("threshold", vad.thresholdRms)
            put("prefix_padding_ms", vad.prefixPaddingMs)
            put("silence_duration_ms", vad.silenceDurationMs)
        }
        VadConfig.Manual -> buildJsonObject { put("type", "none") }
    }

    private fun toolChoiceJson(choice: ToolChoice): kotlinx.serialization.json.JsonElement = when (choice) {
        ToolChoice.Auto -> kotlinx.serialization.json.JsonPrimitive("auto")
        ToolChoice.None -> kotlinx.serialization.json.JsonPrimitive("none")
        ToolChoice.Required -> kotlinx.serialization.json.JsonPrimitive("required")
        is ToolChoice.Named -> buildJsonObject {
            put("type", "function")
            put("name", choice.name)
        }
    }

    /** Encode a PCM16 [AudioFrame] into a base64-wrapped string for the wire. */
    fun encodeAudio(frame: AudioFrame): String = Pcm16AudioCodec.encode(frame)

    /** Decode a base64 string of little-endian PCM16 bytes back into an [AudioFrame]. */
    fun decodeAudio(base64: String, sampleRateHz: Int = AudioFrame.DEFAULT_SAMPLE_RATE_HZ): AudioFrame =
        Pcm16AudioCodec.decode(base64, sampleRateHz)

    /** Extract `type` from a server event payload, throwing if absent. */
    fun readType(payload: JsonObject): String {
        val type = payload["type"]
        require(type != null) { "OpenAI realtime event missing required `type` field: $payload" }
        return type.toString().trim('"')
    }

    /** Extract a JsonArray field safely; returns empty array if absent. */
    fun arrayOrEmpty(obj: JsonObject, key: String): JsonArray =
        (obj[key] as? JsonArray) ?: JsonArray(emptyList())
}
