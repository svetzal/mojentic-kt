package com.mojentic.omlx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class OmlxSettingsTest {
    private fun resolve(
        host: String? = null,
        apiKey: String? = null,
        timeout: Duration? = null,
        environment: Map<String, String> = emptyMap(),
    ): OmlxSettings = OmlxSettings.resolve(host, apiKey, timeout) { environment[it] }

    private val environment = mapOf(
        "OMLX_HOST" to "http://studio.local:9000",
        "OMLX_API_KEY" to "env-key",
        "OMLX_TIMEOUT" to "1500",
    )

    @Test
    fun defaultsApplyWithNoArgumentsAndNoEnvironment() {
        val settings = resolve()

        assertEquals("http://localhost:8000/v1", settings.baseUrl)
        assertNull(settings.apiKey)
        assertEquals(10.minutes, settings.timeout)
        assertEquals(600_000L, DEFAULT_OMLX_TIMEOUT.inWholeMilliseconds)
    }

    @Test
    fun environmentAppliesWhenNoArgumentIsGiven() {
        val settings = resolve(environment = environment)

        assertEquals("http://studio.local:9000/v1", settings.baseUrl)
        assertEquals("env-key", settings.apiKey)
        assertEquals(1500.milliseconds, settings.timeout)
    }

    @Test
    fun explicitArgumentsWinOverEnvironment() {
        val settings = resolve(host = "http://mac.local:8123", apiKey = "arg-key", timeout = 30.seconds, environment = environment)

        assertEquals("http://mac.local:8123/v1", settings.baseUrl)
        assertEquals("arg-key", settings.apiKey)
        assertEquals(30.seconds, settings.timeout)
    }

    @Test
    fun trailingSlashOnHostIsDroppedBeforeV1IsAdded() {
        assertEquals("http://localhost:8000/v1", resolve(host = "http://localhost:8000/").baseUrl)
    }

    @Test
    fun blankEnvironmentValuesCountAsUnset() {
        val settings = resolve(environment = mapOf("OMLX_HOST" to " ", "OMLX_API_KEY" to "", "OMLX_TIMEOUT" to ""))

        assertEquals("http://localhost:8000/v1", settings.baseUrl)
        assertNull(settings.apiKey)
        assertEquals(DEFAULT_OMLX_TIMEOUT, settings.timeout)
    }

    @Test
    fun explicitBlankApiKeySendsNoKeyEvenWhenEnvironmentHasOne() {
        assertNull(resolve(apiKey = "", environment = environment).apiKey)
    }

    @Test
    fun unreadableEnvironmentTimeoutFallsBackToDefault() {
        assertEquals(DEFAULT_OMLX_TIMEOUT, resolve(environment = mapOf("OMLX_TIMEOUT" to "ten minutes")).timeout)
        assertEquals(DEFAULT_OMLX_TIMEOUT, resolve(environment = mapOf("OMLX_TIMEOUT" to "0")).timeout)
    }

    @Test
    fun nonPositiveExplicitTimeoutIsRejected() {
        assertFailsWith<IllegalArgumentException> { resolve(timeout = Duration.ZERO) }
    }
}
