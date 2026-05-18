package com.mojentic.realtime

/**
 * Voice-activity-detection strategy for a realtime session.
 *
 * In [Server] mode the provider decides when the user has stopped talking and
 * automatically requests a response. In [Manual] mode the client decides when
 * to commit the input audio buffer and ask for a response — useful for
 * push-to-talk UIs and deterministic tests.
 */
public sealed interface VadConfig {
    public data class Server(
        val thresholdRms: Double = DEFAULT_THRESHOLD,
        val prefixPaddingMs: Int = DEFAULT_PREFIX_PADDING_MS,
        val silenceDurationMs: Int = DEFAULT_SILENCE_DURATION_MS,
    ) : VadConfig {
        public companion object {
            public const val DEFAULT_THRESHOLD: Double = 0.5
            public const val DEFAULT_PREFIX_PADDING_MS: Int = 300
            public const val DEFAULT_SILENCE_DURATION_MS: Int = 500
        }
    }

    public data object Manual : VadConfig
}
