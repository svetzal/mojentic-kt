package com.mojentic.omlx

import com.mojentic.llm.recovery.RecoveryEvent
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryReason
import com.mojentic.llm.recovery.RecoveryRetryAfter
import com.mojentic.llm.recovery.RecoveryStage
import com.mojentic.llm.recovery.RecoveryWire
import com.mojentic.omlx.RecoveryTestFixtures.Provider
import com.mojentic.omlx.RecoveryTestFixtures.call
import com.mojentic.omlx.RecoveryTestFixtures.matrix
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Production transport acceptance through ordinary and structured public adapters. */
class RecoveryTransportHttpTest {
    @Test
    fun oneAttempt503ZeroRetainsExactFailureWithoutHiddenResend(): Unit = runBlocking {
        matrix { provider, structured ->
            val events = mutableListOf<RecoveryEvent>()
            val wires = mutableListOf<RecoveryWire>()
            RecoveryScriptedServer(listOf(ScriptedReply(503, "private-busy", mapOf("Retry-After" to "0")), provider.success(structured)))
                .use { server ->
                    provider.gateway(server.url).use { gateway ->
                        val failure = assertFailsWith<RecoveryException> {
                            call(
                                gateway.value,
                                structured,
                                RecoveryPolicy(
                                    maxAttempts = 1,
                                    admission = { true },
                                    observer = { events += it },
                                    capture = { wires += it },
                                ),
                            )
                        }
                        assertEquals(RecoveryReason.EXHAUSTED, failure.reason)
                        assertEquals(1, server.requests.size)
                        assertEquals(1, failure.wireAttempts)
                        val retained = failure.failures.single()
                        assertEquals(503, retained.status)
                        assertEquals(RecoveryRetryAfter.Delay(0), retained.retryAfter)
                        assertEquals(
                            listOf("0"),
                            retained.inspectHeaders().entries.single {
                                it.key.equals("Retry-After", ignoreCase = true)
                            }.value,
                        )
                        assertEquals("private-busy", retained.inspectBytes().decodeToString())
                        assertEquals(listOf(RecoveryStage.STARTED, RecoveryStage.FAILED, RecoveryStage.EXHAUSTED), events.map { it.stage })
                        assertEquals(retained.identity, events.first().identity)
                        assertEquals(retained.identity, wires.first().identity)
                        assertContentEquals(server.requests.single(), wires.first().inspectRequest())
                        assertEquals(listOf(503), wires.filter { it.complete }.map { it.status })
                        assertEquals(listOf(retained), events.last().failures)
                        assertFalse("private" in Json.encodeToString(failure.summary()))
                    }
                }
        }
    }

    @Test
    fun compressed503PreservesDecodedFailureAndRetryAfterWithoutResend(): Unit = runBlocking {
        matrix { provider, structured ->
            val wires = mutableListOf<RecoveryWire>()
            RecoveryScriptedServer(
                listOf(ScriptedReply(503, "private-compressed", mapOf("Retry-After" to "0"), gzip = true), provider.success(structured)),
            ).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val failure = assertFailsWith<RecoveryException> {
                        call(gateway.value, structured, RecoveryPolicy(maxAttempts = 1, capture = { wires += it }))
                    }
                    val retained = failure.failures.single()
                    assertEquals(503, retained.status)
                    assertEquals(RecoveryRetryAfter.Delay(0), retained.retryAfter)
                    assertEquals("private-compressed", retained.inspectBytes().decodeToString())
                    assertFalse(
                        retained.inspectHeaders().keys.any {
                            it.equals("Content-Encoding", true) ||
                                it.equals("Content-Length", true)
                        },
                    )
                    assertEquals(1, server.requests.size)
                    assertEquals(1, failure.wireAttempts)
                    assertContentEquals(server.requests.single(), wires.first().inspectRequest())
                    assertEquals(listOf(503), wires.filter { it.complete }.map { it.status })
                }
            }
        }
    }

    @Test
    fun healthyGenerationBeyondTenSecondsKeepsProviderSocketSettings(): Unit = runBlocking {
        matrix { provider, structured ->
            val events = mutableListOf<RecoveryEvent>()
            val wires = mutableListOf<RecoveryWire>()
            RecoveryScriptedServer(listOf(provider.success(structured).copy(delayMillis = 11_000))).use { server ->
                provider.gateway(server.url, 15_000.milliseconds).use { gateway ->
                    val response = call(
                        gateway.value,
                        structured,
                        RecoveryPolicy(
                            maxAttempts = 1,
                            budgetMillis = 100,
                            observer = { events += it },
                            capture = { wires += it },
                        ),
                    )
                    assertEquals(if (structured) "{\"value\":\"ok\"}" else "ok", response.content)
                    assertEquals(1, server.requests.size)
                    assertContentEquals(server.requests.single(), wires.first().inspectRequest())
                    assertEquals(listOf(200), wires.filter { it.complete }.map { it.status })
                    assertEquals(listOf(RecoveryStage.STARTED, RecoveryStage.SUCCEEDED), events.map { it.stage })
                }
            }
        }
    }

    @Test
    fun shorterOmlxSocketTimeoutRetainsCauseWithoutUnauthorizedResend(): Unit = runBlocking {
        for (structured in listOf(false, true)) {
            val events = mutableListOf<RecoveryEvent>()
            val wires = mutableListOf<RecoveryWire>()
            RecoveryScriptedServer(listOf(Provider.OMLX.success(structured).copy(delayMillis = 700))).use { server ->
                Provider.OMLX.gateway(server.url, 200.milliseconds).use { gateway ->
                    val failure = assertFailsWith<RecoveryException> {
                        call(
                            gateway.value,
                            structured,
                            RecoveryPolicy(
                                maxAttempts = 2,
                                observer = { events += it },
                                capture = { wires += it },
                            ),
                        )
                    }
                    assertEquals(RecoveryReason.ADMISSION_REQUIRED, failure.reason)
                    assertEquals(1, failure.wireAttempts)
                    assertEquals(1, server.requests.size)
                    val retained = failure.failures.single()
                    assertTrue(retained.inspectCause() is java.net.SocketTimeoutException)
                    assertNull(retained.status)
                    assertEquals(RecoveryReason.TRANSPORT, retained.reason)
                    assertEquals(retained.identity, wires.first().identity)
                    assertContentEquals(server.requests.single(), wires.first().inspectRequest())
                    assertEquals(listOf(RecoveryStage.STARTED, RecoveryStage.FAILED, RecoveryStage.EXHAUSTED), events.map { it.stage })
                }
            }
        }
    }
}
