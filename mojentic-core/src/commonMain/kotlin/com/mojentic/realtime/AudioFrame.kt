package com.mojentic.realtime

/**
 * One frame of 16-bit signed PCM audio.
 *
 * Realtime providers ship audio as base64-encoded little-endian PCM16 mono;
 * the gateway is responsible for that wire-level (un)framing so consumers only
 * ever deal with raw 16-bit samples here.
 *
 * @property samples Interleaved little-endian PCM16 samples (mono, single
 *   channel). For OpenAI Realtime the default sample rate is 24 kHz.
 * @property sampleRateHz Sample rate in Hertz. Defaults to 24 000 — OpenAI
 *   Realtime's default for `pcm16` audio.
 */
public data class AudioFrame(
    val samples: ShortArray,
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        if (sampleRateHz != other.sampleRateHz) return false
        return samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int = 31 * samples.contentHashCode() + sampleRateHz

    public companion object {
        public const val DEFAULT_SAMPLE_RATE_HZ: Int = 24_000
    }
}
