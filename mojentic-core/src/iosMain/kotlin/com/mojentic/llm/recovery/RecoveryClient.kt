package com.mojentic.llm.recovery

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

public actual fun recoveryHttpClient(engine: HttpClientEngine?): HttpClient =
    if (engine != null) {
        HttpClient(engine) { followRedirects = false }
    } else {
        HttpClient(Darwin) {
            followRedirects = false
        }
    }
