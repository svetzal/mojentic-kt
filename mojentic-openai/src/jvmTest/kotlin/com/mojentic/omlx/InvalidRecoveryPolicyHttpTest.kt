package com.mojentic.omlx

import com.mojentic.errors.InvalidRecoveryPolicyException
import com.mojentic.errors.RecoveryPolicyViolation
import com.mojentic.llm.ChatSession
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import com.mojentic.omlx.AdmissionAmendmentHttpTest.Entry
import com.mojentic.omlx.RecoveryTestFixtures.Provider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvalidRecoveryPolicyHttpTest {
    @Test
    fun invalidPolicyRejectsBeforeSessionHttpAndCallbacks(): Unit = runBlocking {
        RecoveryScriptedServer(listOf(RecoveryTestFixtures.Provider.OPENAI.success(false))).use { server ->
            RecoveryTestFixtures.Provider.OPENAI.gateway(server.url).use { gateway ->
                val callbacks = mutableListOf<String>()
                suspend fun send(attempts: Int): String? {
                    val policy = RecoveryPolicy(
                        maxAttempts = attempts,
                        admission = {
                            callbacks += "admission"
                            true
                        },
                        observer = { callbacks += "observer" },
                        capture = { callbacks += "capture" },
                    )
                    return ChatSession(LlmBroker(gateway.value), "test-model", config = CompletionConfig(recovery = policy))
                        .send("private-request").content
                }
                val failure = assertFailsWith<InvalidRecoveryPolicyException> { send(0) }
                assertEquals(RecoveryPolicyViolation.NON_POSITIVE_MAX_ATTEMPTS, failure.violation)
                assertNull(failure.cause)
                assertEquals(emptyList(), server.requests)
                assertEquals(emptyList(), callbacks)
                assertEquals("ok", send(1))
                assertEquals(1, server.requests.size)
            }
        }
    }

    @Test
    fun zeroAttemptsRejectsBeforeEveryPublicSurface(): Unit = verify(RecoveryPolicyViolation.NON_POSITIVE_MAX_ATTEMPTS) { calls ->
        RecoveryPolicy(
            maxAttempts = 0,
            admission = {
                calls += "admission"
                true
            },
            observer = { calls += "observer" },
            capture = { calls += "capture" },
        )
    }

    @Test
    fun negativeAttemptsRejectsBeforeEveryPublicSurface(): Unit = verify(RecoveryPolicyViolation.NON_POSITIVE_MAX_ATTEMPTS) { calls ->
        RecoveryPolicy(
            maxAttempts = Int.MIN_VALUE,
            admission = {
                calls += "admission"
                true
            },
            observer = { calls += "observer" },
            capture = { calls += "capture" },
        )
    }

    @Test
    fun negativeBaseDelayRejectsBeforeEveryPublicSurface(): Unit = verify(RecoveryPolicyViolation.NEGATIVE_BASE_DELAY) { calls ->
        RecoveryPolicy(
            baseDelayMillis = -1,
            admission = {
                calls += "admission"
                true
            },
            observer = { calls += "observer" },
            capture = { calls += "capture" },
        )
    }

    @Test
    fun ceilingBelowBaseRejectsBeforeEveryPublicSurface(): Unit = verify(RecoveryPolicyViolation.CEILING_BELOW_BASE_DELAY) { calls ->
        RecoveryPolicy(
            baseDelayMillis = 1,
            delayCeilingMillis = 0,
            admission = {
                calls += "admission"
                true
            },
            observer = { calls += "observer" },
            capture = { calls += "capture" },
        )
    }

    @Test
    fun negativeCeilingRejectsBeforeEveryPublicSurface(): Unit = verify(RecoveryPolicyViolation.CEILING_BELOW_BASE_DELAY) { calls ->
        RecoveryPolicy(
            baseDelayMillis = 0,
            delayCeilingMillis = -1,
            admission = {
                calls += "admission"
                true
            },
            observer = { calls += "observer" },
            capture = { calls += "capture" },
        )
    }

    @Test
    fun maximumLongCeilingRejectsBeforeEveryPublicSurface(): Unit = verify(RecoveryPolicyViolation.UNBOUNDED_DELAY_CEILING) { calls ->
        RecoveryPolicy(
            delayCeilingMillis = Long.MAX_VALUE,
            admission = {
                calls += "admission"
                true
            },
            observer = { calls += "observer" },
            capture = { calls += "capture" },
        )
    }

    @Test
    fun negativeBudgetRejectsBeforeEveryPublicSurface(): Unit = verify(RecoveryPolicyViolation.NEGATIVE_BUDGET) { calls ->
        RecoveryPolicy(
            budgetMillis = -1,
            admission = {
                calls += "admission"
                true
            },
            observer = { calls += "observer" },
            capture = { calls += "capture" },
        )
    }

    private fun verify(violation: RecoveryPolicyViolation, invalid: (MutableList<String>) -> RecoveryPolicy): Unit = runBlocking {
        for (provider in Provider.entries) {
            for (entry in Entry.entries) {
                RecoveryScriptedServer(listOf(entry.success(provider))).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        val callbacks = mutableListOf<String>()
                        val tools = listOf(object : LlmTool {
                            override val descriptor = ToolDescriptor("sentinel", "private-tool", JsonObject(emptyMap()))
                            override suspend fun execute(arguments: JsonObject): String {
                                callbacks += "tool"
                                return "private-result"
                            }
                        })
                        val failure = assertFailsWith<InvalidRecoveryPolicyException> {
                            entry.call(gateway, invalid(callbacks), tools)
                        }
                        assertEquals(violation, failure.violation)
                        assertNull(failure.cause)
                        assertEquals(emptyList(), failure.suppressedExceptions)
                        assertFalse("private" in failure.toString())
                        assertFalse("credential-secret" in failure.toString())
                        assertTrue(server.requests.isEmpty())
                        assertTrue(callbacks.isEmpty())
                        // A real success through the same surface proves the queued boundary is reachable.
                        entry.call(
                            gateway,
                            RecoveryPolicy(
                                baseDelayMillis = 0,
                                delayCeilingMillis = 0,
                                observer = { callbacks += "observer" },
                                capture = { callbacks += "capture" },
                            ),
                            tools,
                        )
                        assertEquals(1, server.requests.size)
                        assertTrue("observer" in callbacks)
                        assertTrue("capture" in callbacks)
                        assertFalse("tool" in callbacks)
                        assertTrue(server.errors.isEmpty())
                    }
                }
            }
        }
    }
}
