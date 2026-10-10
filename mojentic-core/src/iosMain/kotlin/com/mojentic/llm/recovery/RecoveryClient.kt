package com.mojentic.llm.recovery

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig

public actual fun recoveryHttpClient(engine: HttpClientEngine?): HttpClient = recoveryHttpClient(engine, null)

public actual fun recoveryHttpClient(engine: HttpClientEngine?, timeoutMillis: Long?): HttpClient =
    if (engine != null) {
        HttpClient(engine) {
            followRedirects = false
            install(HttpTimeout) {
                connectTimeoutMillis = timeoutMillis
                socketTimeoutMillis = timeoutMillis ?: HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
        }
    } else {
        HttpClient(Darwin) {
            followRedirects = false
            install(HttpTimeout) {
                connectTimeoutMillis = timeoutMillis
                socketTimeoutMillis = timeoutMillis ?: HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
        }
    }
