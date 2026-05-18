package com.mojentic.realtime

import com.mojentic.llm.tools.LlmTool

/**
 * Vendor-neutral configuration for a realtime voice session.
 *
 * Maps onto a provider's session-config message (for OpenAI Realtime, the
 * initial `session.update` event). Only the cross-port subset is surfaced;
 * provider-specific knobs go through [providerExtras].
 *
 * @property instructions System prompt analogue for the session.
 * @property voice Provider voice name (e.g. `alloy`, `verse`, `shimmer`).
 * @property modalities Output modalities the model should produce. Defaults
 *   to `[audio, text]`.
 * @property audioFormat Sample format used for ingress and egress audio.
 * @property turnDetection Voice-activity-detection strategy.
 * @property tools Tools the session may call.
 * @property toolChoice Provider-neutral tool-choice hint.
 * @property temperature Sampling temperature, when supported.
 * @property maxResponseOutputTokens Cap on tokens per assistant response.
 * @property providerExtras Escape hatch for provider-specific options;
 *   merged verbatim into the gateway's session-update payload.
 */
public data class RealtimeVoiceConfig(
    val instructions: String? = null,
    val voice: String? = null,
    val modalities: Set<RealtimeModality> = setOf(RealtimeModality.AUDIO, RealtimeModality.TEXT),
    val audioFormat: RealtimeAudioFormat = RealtimeAudioFormat.PCM16,
    val turnDetection: VadConfig = VadConfig.Server(),
    val tools: List<LlmTool> = emptyList(),
    val toolChoice: ToolChoice = ToolChoice.Auto,
    val temperature: Double? = null,
    val maxResponseOutputTokens: Int? = null,
    val inputAudioTranscriptionModel: String? = "whisper-1",
    val providerExtras: Map<String, String> = emptyMap(),
)

/**
 * Output modality the model is permitted to produce in a realtime session.
 */
public enum class RealtimeModality {
    AUDIO,
    TEXT,
    ;

    public val wireValue: String
        get() = name.lowercase()
}

/**
 * Audio sample format for realtime ingress / egress.
 *
 * `PCM16` is the default and the only format guaranteed across providers;
 * the telephony codecs are surfaced for completeness.
 */
public enum class RealtimeAudioFormat {
    PCM16,
    G711_ULAW,
    G711_ALAW,
    ;

    public val wireValue: String
        get() = name.lowercase()
}

/**
 * Provider-neutral tool-choice hint for the session.
 */
public sealed interface ToolChoice {
    public data object Auto : ToolChoice
    public data object None : ToolChoice
    public data object Required : ToolChoice
    public data class Named(val name: String) : ToolChoice
}
