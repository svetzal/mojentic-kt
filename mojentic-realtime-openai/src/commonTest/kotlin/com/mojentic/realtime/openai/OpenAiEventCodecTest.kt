package com.mojentic.realtime.openai

import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import com.mojentic.realtime.AudioFrame
import com.mojentic.realtime.ClientRealtimeEvent
import com.mojentic.realtime.RealtimeAudioFormat
import com.mojentic.realtime.RealtimeModality
import com.mojentic.realtime.RealtimeVoiceConfig
import com.mojentic.realtime.ToolChoice
import com.mojentic.realtime.VadConfig
import com.mojentic.realtime.openai.internal.OpenAiEventCodec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class EchoTool : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "echo",
        description = "Echoes its input",
        parameters = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { put("text", buildJsonObject { put("type", "string") }) })
        },
    )

    override suspend fun execute(arguments: JsonObject): String = arguments.toString()
}

class OpenAiEventCodecTest {

    @Test
    fun encodesUserTextAsConversationItemCreate() {
        val wire = OpenAiEventCodec.clientEventToWire(ClientRealtimeEvent.UserText("Hello, voice agent."))
        assertEquals("conversation.item.create", wire["type"]!!.jsonPrimitive.content)
        val item = wire["item"]!!.jsonObject
        assertEquals("message", item["type"]!!.jsonPrimitive.content)
        assertEquals("user", item["role"]!!.jsonPrimitive.content)
        val content = item["content"]!!.jsonArray
        assertEquals(1, content.size)
        val first = content[0].jsonObject
        assertEquals("input_text", first["type"]!!.jsonPrimitive.content)
        assertEquals("Hello, voice agent.", first["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun encodesFunctionCallOutputItem() {
        val wire = OpenAiEventCodec.clientEventToWire(
            ClientRealtimeEvent.FunctionCallOutput(callId = "call_42", output = "{\"ok\":true}"),
        )
        val item = wire["item"]!!.jsonObject
        assertEquals("function_call_output", item["type"]!!.jsonPrimitive.content)
        assertEquals("call_42", item["call_id"]!!.jsonPrimitive.content)
        assertEquals("{\"ok\":true}", item["output"]!!.jsonPrimitive.content)
    }

    @Test
    fun encodesResponseControlEvents() {
        assertEquals(
            "response.create",
            OpenAiEventCodec.clientEventToWire(ClientRealtimeEvent.ResponseCreate)["type"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "response.cancel",
            OpenAiEventCodec.clientEventToWire(ClientRealtimeEvent.ResponseCancel)["type"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "input_audio_buffer.commit",
            OpenAiEventCodec.clientEventToWire(ClientRealtimeEvent.InputAudioBufferCommit)["type"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "input_audio_buffer.clear",
            OpenAiEventCodec.clientEventToWire(ClientRealtimeEvent.InputAudioBufferClear)["type"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun sessionUpdateCarriesConfigAndTools() {
        val config = RealtimeVoiceConfig(
            instructions = "Be concise.",
            voice = "verse",
            modalities = setOf(RealtimeModality.AUDIO, RealtimeModality.TEXT),
            audioFormat = RealtimeAudioFormat.PCM16,
            turnDetection = VadConfig.Server(thresholdRms = 0.4, prefixPaddingMs = 250, silenceDurationMs = 700),
            tools = listOf(EchoTool()),
            toolChoice = ToolChoice.Required,
            temperature = 0.6,
            maxResponseOutputTokens = 256,
        )
        val wire = OpenAiEventCodec.clientEventToWire(ClientRealtimeEvent.SessionUpdate(config))
        assertEquals("session.update", wire["type"]!!.jsonPrimitive.content)
        val session = wire["session"]!!.jsonObject
        assertEquals("Be concise.", session["instructions"]!!.jsonPrimitive.content)
        assertEquals("verse", session["voice"]!!.jsonPrimitive.content)
        assertEquals("pcm16", session["input_audio_format"]!!.jsonPrimitive.content)
        assertEquals("pcm16", session["output_audio_format"]!!.jsonPrimitive.content)
        assertEquals(0.6, session["temperature"]!!.jsonPrimitive.content.toDouble())
        assertEquals(256, session["max_response_output_tokens"]!!.jsonPrimitive.content.toInt())
        val turn = session["turn_detection"]!!.jsonObject
        assertEquals("server_vad", turn["type"]!!.jsonPrimitive.content)
        assertEquals(0.4, turn["threshold"]!!.jsonPrimitive.content.toDouble())
        assertEquals(250, turn["prefix_padding_ms"]!!.jsonPrimitive.content.toInt())
        assertEquals(700, turn["silence_duration_ms"]!!.jsonPrimitive.content.toInt())
        val tools = session["tools"]!!.jsonArray
        assertEquals(1, tools.size)
        val tool = tools[0].jsonObject
        assertEquals("function", tool["type"]!!.jsonPrimitive.content)
        assertEquals("echo", tool["name"]!!.jsonPrimitive.content)
        assertEquals("Echoes its input", tool["description"]!!.jsonPrimitive.content)
        // parameters carried through verbatim
        assertEquals("object", tool["parameters"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        // tool_choice as string
        assertEquals("required", session["tool_choice"]!!.jsonPrimitive.content)
    }

    @Test
    fun manualVadEncodesAsTypeNone() {
        val config = RealtimeVoiceConfig(turnDetection = VadConfig.Manual)
        val wire = OpenAiEventCodec.clientEventToWire(ClientRealtimeEvent.SessionUpdate(config))
        val turn = wire["session"]!!.jsonObject["turn_detection"]!!.jsonObject
        assertEquals("none", turn["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun namedToolChoiceEncodesAsObject() {
        val config = RealtimeVoiceConfig(toolChoice = ToolChoice.Named("echo"))
        val wire = OpenAiEventCodec.clientEventToWire(ClientRealtimeEvent.SessionUpdate(config))
        val tc = wire["session"]!!.jsonObject["tool_choice"]!!.jsonObject
        assertEquals("function", tc["type"]!!.jsonPrimitive.content)
        assertEquals("echo", tc["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun nullInstructionsAndVoiceAreOmitted() {
        val config = RealtimeVoiceConfig(instructions = null, voice = null)
        val wire = OpenAiEventCodec.clientEventToWire(ClientRealtimeEvent.SessionUpdate(config))
        val session = wire["session"]!!.jsonObject
        assertNull(session["instructions"])
        assertNull(session["voice"])
    }

    @Test
    fun audioFrameRoundTripsViaBase64() {
        val original = AudioFrame(
            samples = shortArrayOf(0, 1, -1, 32767, -32768, 1234, -4321),
            sampleRateHz = 24_000,
        )
        val encoded = OpenAiEventCodec.encodeAudio(original)
        assertTrue(encoded.isNotEmpty())
        val decoded = OpenAiEventCodec.decodeAudio(encoded, sampleRateHz = 24_000)
        assertContentEquals(original.samples, decoded.samples)
        assertEquals(24_000, decoded.sampleRateHz)
    }

    @Test
    fun audioBufferAppendCarriesBase64Payload() {
        val frame = AudioFrame(samples = shortArrayOf(100, -200, 300))
        val wire = OpenAiEventCodec.clientEventToWire(ClientRealtimeEvent.InputAudioBufferAppend(frame))
        assertEquals("input_audio_buffer.append", wire["type"]!!.jsonPrimitive.content)
        val encoded = wire["audio"]!!.jsonPrimitive.content
        val decoded = OpenAiEventCodec.decodeAudio(encoded)
        assertContentEquals(frame.samples, decoded.samples)
    }

    @Test
    fun readTypeExtractsDiscriminator() {
        val payload = buildJsonObject {
            put("type", "response.created")
            put("event_id", "evt_1")
        }
        assertEquals("response.created", OpenAiEventCodec.readType(payload))
    }

    @Test
    fun providerExtrasAreMergedIntoSessionConfig() {
        val config = RealtimeVoiceConfig(providerExtras = mapOf("custom_flag" to "yes"))
        val wire = OpenAiEventCodec.clientEventToWire(ClientRealtimeEvent.SessionUpdate(config))
        val session = wire["session"]!!.jsonObject
        assertEquals("yes", session["custom_flag"]!!.jsonPrimitive.content)
    }

    @Test
    fun encodedAudioStringIsValidBase64() {
        val frame = AudioFrame(samples = shortArrayOf(1, 2, 3, 4))
        val encoded = OpenAiEventCodec.encodeAudio(frame)
        assertTrue(encoded.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' })
        assertEquals(0, encoded.length % 4)
    }

    @Test
    fun rejectsOddLengthAudioPayload() {
        var threw = false
        try {
            OpenAiEventCodec.decodeAudio("AA==")
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw, "decoding an odd-length PCM16 payload should fail loudly")
    }
}
