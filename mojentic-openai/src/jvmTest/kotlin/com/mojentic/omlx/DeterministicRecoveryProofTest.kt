package com.mojentic.omlx

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.recovery.RecoveryEvent
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryStage
import com.mojentic.llm.recovery.RecoveryWire
import com.mojentic.openai.OpenAIGateway
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeterministicRecoveryProofTest {
    @Test
    fun retryPreservesPayloadIdentityFailureAndLifecycle() = runTest {
        val requests = mutableListOf<ByteArray>()
        val events = mutableListOf<RecoveryEvent>()
        val wires = mutableListOf<RecoveryWire>()
        val sleeps = mutableListOf<Long>()
        val engine = MockEngine { request ->
            requests += (request.body as TextContent).text.encodeToByteArray()
            if (requests.size == 1) {
                respond("private-failure", HttpStatusCode.ServiceUnavailable, headersOf("Retry-After", "0"))
            } else {
                respond("""{"choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}""")
            }
        }
        val gateway = OpenAIGateway("credential-secret", engine = engine)
        try {
            val result = gateway.complete(
                "gpt-4o",
                listOf(LlmMessage.user("private-request")),
                null,
                CompletionConfig(
                    recovery = RecoveryPolicy(
                        maxAttempts = 2,
                        admission = { true },
                        observer = { events += it },
                        capture = { wires += it },
                        clock = { 0 },
                        monotonicClock = { testScheduler.currentTime },
                        sleep = { sleeps += it },
                        jitter = { it },
                    ),
                ),
            )
            assertEquals("ok", result.content)
            assertRecovery(requests, events, wires, sleeps)
        } finally {
            gateway.close()
        }
    }

    private fun assertRecovery(
        requests: List<ByteArray>,
        events: List<RecoveryEvent>,
        wires: List<RecoveryWire>,
        sleeps: List<Long>,
    ) {
        assertEquals(2, requests.size)
        assertContentEquals(requests[0], requests[1])
        assertEquals(listOf(100L), sleeps)
        assertEquals(
            listOf(
                RecoveryStage.STARTED,
                RecoveryStage.FAILED,
                RecoveryStage.ADMISSION_PENDING,
                RecoveryStage.ADMITTED,
                RecoveryStage.SCHEDULED,
                RecoveryStage.STARTED,
                RecoveryStage.SUCCEEDED,
            ),
            events.map { it.stage },
        )
        val first = events.first().identity
        val second = events.last().identity
        assertEquals(first.logicalId, second.logicalId)
        assertNotEquals(first.attemptId, second.attemptId)
        assertContentEquals("private-failure".encodeToByteArray(), events.last().failures.single().inspectBytes())
        assertContentEquals(requests[0], wires.first().inspectRequest())
        assertFalse("private" in events.toString())
        assertFalse("credential-secret" in wires.toString())
    }
}
