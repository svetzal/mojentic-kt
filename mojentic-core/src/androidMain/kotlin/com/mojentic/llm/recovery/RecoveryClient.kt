package com.mojentic.llm.recovery

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.http.HttpStatusCode

public actual fun recoveryHttpClient(engine: HttpClientEngine?): HttpClient = recoveryHttpClient(engine, null)

public actual fun recoveryHttpClient(engine: HttpClientEngine?, timeoutMillis: Long?): HttpClient {
    val configure: HttpClientConfig<*>.() -> Unit = {
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = timeoutMillis
            socketTimeoutMillis = timeoutMillis ?: HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        }
    }
    return if (engine != null) {
        HttpClient(engine, configure)
    } else {
        HttpClient(OkHttp) {
            configure()
            engine {
                config {
                    retryOnConnectionFailure(false)
                    followRedirects(false)
                    followSslRedirects(false)
                    // OkHttp's 503/Retry-After: 0 follow-up ignores retryOnConnectionFailure.
                    // Hide that header only from its follow-up layer, then restore its exact values.
                    // Keep the normal response decoding headers.
                    addInterceptor { chain ->
                        val evidence = RecoveryRetryAfterEvidence()
                        val request = chain.request().newBuilder().tag(RecoveryRetryAfterEvidence::class.java, evidence).build()
                        val response = chain.proceed(request)
                        evidence.values?.let { values ->
                            response.newBuilder().apply {
                                values.forEach { addHeader("Retry-After", it) }
                            }.build()
                        } ?: response
                    }
                    addNetworkInterceptor { chain ->
                        val response = chain.proceed(chain.request())
                        if (response.code == HttpStatusCode.ServiceUnavailable.value) {
                            chain.request().tag(RecoveryRetryAfterEvidence::class.java)?.values = response.headers.values("Retry-After")
                            response.newBuilder().removeHeader("Retry-After").build()
                        } else {
                            response
                        }
                    }
                }
            }
        }
    }
}

private class RecoveryRetryAfterEvidence {
    var values: List<String>? = null
}
