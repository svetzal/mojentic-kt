package com.mojentic.omlx

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val logger = KotlinLogging.logger {}

internal const val OMLX_HOST_VARIABLE = "OMLX_HOST"
internal const val OMLX_API_KEY_VARIABLE = "OMLX_API_KEY"
internal const val OMLX_TIMEOUT_VARIABLE = "OMLX_TIMEOUT"

/**
 * Resolved oMLX connection settings.
 *
 * @property baseUrl Host plus `/v1`, with no trailing slash.
 * @property apiKey Bearer token, or null to send no authorization header.
 * @property timeout Connect and socket timeout for every request.
 */
internal data class OmlxSettings(
    val baseUrl: String,
    val apiKey: String?,
    val timeout: Duration,
) {
    companion object {
        /**
         * Resolves each setting as: explicit value, then [environment], then default.
         *
         * A blank host or key counts as unset in the environment. An explicit
         * blank key sends no authorization header, even when `OMLX_API_KEY` is
         * set. `OMLX_TIMEOUT` is in milliseconds; a value that is not a
         * positive whole number is ignored with a warning.
         */
        fun resolve(
            host: String?,
            apiKey: String?,
            timeout: Duration?,
            environment: (String) -> String?,
        ): OmlxSettings {
            require(timeout == null || timeout.isPositive()) { "oMLX timeout must be positive, was $timeout" }
            val resolvedHost = host ?: environment(OMLX_HOST_VARIABLE)?.takeIf { it.isNotBlank() } ?: DEFAULT_OMLX_HOST
            return OmlxSettings(
                baseUrl = resolvedHost.trim().trimEnd('/') + "/v1",
                apiKey = (apiKey ?: environment(OMLX_API_KEY_VARIABLE))?.takeIf { it.isNotBlank() },
                timeout = timeout ?: environmentTimeout(environment) ?: DEFAULT_OMLX_TIMEOUT,
            )
        }

        private fun environmentTimeout(environment: (String) -> String?): Duration? {
            val raw = environment(OMLX_TIMEOUT_VARIABLE)?.takeIf { it.isNotBlank() } ?: return null
            val millis = raw.trim().toLongOrNull()?.takeIf { it > 0 }
            if (millis == null) logger.warn { "Ignoring $OMLX_TIMEOUT_VARIABLE=$raw: expected a positive number of milliseconds" }
            return millis?.milliseconds
        }
    }
}
