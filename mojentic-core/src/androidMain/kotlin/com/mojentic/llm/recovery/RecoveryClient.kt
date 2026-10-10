package com.mojentic.llm.recovery

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

public actual fun recoveryHttpClient(engine: HttpClientEngine?): HttpClient =
    if (engine != null) {
        HttpClient(engine) { followRedirects = false }
    } else {
        HttpClient(OkHttp) {
            followRedirects = false
            engine {
                config {
                    retryOnConnectionFailure(false)
                    followRedirects(false)
                    followSslRedirects(false)
                }
            }
        }
    }
