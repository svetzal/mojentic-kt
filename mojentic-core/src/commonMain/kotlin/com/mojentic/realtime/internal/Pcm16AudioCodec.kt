package com.mojentic.realtime.internal

import com.mojentic.errors.RealtimeGatewayException
import com.mojentic.realtime.AudioFrame
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Vendor-neutral encoding for PCM16 audio frames carried as base64 payloads.
 *
 * Lives in core (rather than per-provider gateways) because the broker needs
 * to decode audio deltas when normalising realtime events, and the encoding
 * itself is provider-agnostic — every realtime API currently in scope
 * (OpenAI, prospective Anthropic, Google) frames PCM16 as little-endian
 * 16-bit samples behind base64.
 */
@OptIn(ExperimentalEncodingApi::class)
public object Pcm16AudioCodec {

    private const val BYTES_PER_SAMPLE: Int = 2
    private const val BITS_PER_BYTE: Int = 8
    private const val BYTE_MASK: Int = 0xFF

    /** Encode a PCM16 [AudioFrame] into a base64 string. */
    public fun encode(frame: AudioFrame): String {
        val bytes = ByteArray(frame.samples.size * BYTES_PER_SAMPLE)
        for (i in frame.samples.indices) {
            val sample = frame.samples[i].toInt()
            bytes[i * BYTES_PER_SAMPLE] = (sample and BYTE_MASK).toByte()
            bytes[i * BYTES_PER_SAMPLE + 1] = ((sample shr BITS_PER_BYTE) and BYTE_MASK).toByte()
        }
        return Base64.encode(bytes)
    }

    /** Decode a base64 string of little-endian PCM16 bytes back into an [AudioFrame]. */
    public fun decode(base64: String, sampleRateHz: Int = AudioFrame.DEFAULT_SAMPLE_RATE_HZ): AudioFrame {
        val bytes = try {
            Base64.decode(base64)
        } catch (failure: IllegalArgumentException) {
            throw RealtimeGatewayException("Failed to decode base64 audio payload", failure)
        }
        require(bytes.size % BYTES_PER_SAMPLE == 0) {
            "PCM16 audio payload must be an even number of bytes (got ${bytes.size})"
        }
        val samples = ShortArray(bytes.size / BYTES_PER_SAMPLE)
        for (i in samples.indices) {
            val lo = bytes[i * BYTES_PER_SAMPLE].toInt() and BYTE_MASK
            val hi = bytes[i * BYTES_PER_SAMPLE + 1].toInt() and BYTE_MASK
            samples[i] = ((hi shl BITS_PER_BYTE) or lo).toShort()
        }
        return AudioFrame(samples = samples, sampleRateHz = sampleRateHz)
    }
}
