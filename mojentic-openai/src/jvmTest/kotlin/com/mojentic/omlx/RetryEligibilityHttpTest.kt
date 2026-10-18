package com.mojentic.omlx

import com.mojentic.anthropic.AnthropicGateway
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmGateway
import com.mojentic.llm.recovery.RecoveryCategory
import com.mojentic.llm.recovery.RecoveryEvent
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryFailure
import com.mojentic.llm.recovery.RecoveryOperation
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryReason
import com.mojentic.llm.recovery.RecoveryStage
import com.mojentic.llm.recovery.RecoveryWire
import com.mojentic.omlx.RecoveryTestFixtures.OwnedGateway
import com.mojentic.omlx.RecoveryTestFixtures.Provider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** kt-retry-eligibility-v1: differential public HTTP tests, with in-process transports. */
class RetryEligibilityHttpTest {
    private enum class Adapter {
        OPENAI,
        ANTHROPIC,
        OLLAMA,
        OMLX,
        ;

        fun gateway(url: String, engine: io.ktor.client.engine.HttpClientEngine): OwnedGateway = when (this) {
            OPENAI -> Provider.OPENAI.gateway(url, engine = engine)

            OLLAMA -> Provider.OLLAMA.gateway(url, engine = engine)

            OMLX -> Provider.OMLX.gateway(url, engine = engine)

            ANTHROPIC -> {
                val gateway = AnthropicGateway("credential-secret", url, engine = engine)
                OwnedGateway(gateway, gateway::close)
            }
        }

        fun success(caller: Caller): ScriptedReply {
            if (this != ANTHROPIC) {
                val provider = when (this) {
                    OPENAI -> Provider.OPENAI
                    OLLAMA -> Provider.OLLAMA
                    else -> Provider.OMLX
                }
                val reply = provider.success(caller == Caller.STRUCTURED)
                if (caller != Caller.STREAM) return reply
                return ScriptedReply(
                    200,
                    if (this == OLLAMA) {
                        Json.parseToJsonElement(reply.body).toString() + "\n"
                    } else {
                        "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}" +
                            "\n\ndata: [DONE]\n\n"
                    },
                )
            }
            val body = when (caller) {
                Caller.COMPLETE -> """{"content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn"}"""

                Caller.STRUCTURED ->
                    """{"content":[{"type":"tool_use","id":"result","name":"respond_in_json",
                    "input":{"value":"ok"}}],"stop_reason":"tool_use"}"""

                Caller.STREAM ->
                    frame("""{"type":"message_start","message":{"model":"test-model","content":[]}}""") +
                        frame("""{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""") +
                        frame("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"ok"}}""") +
                        frame("""{"type":"content_block_stop","index":0}""") +
                        frame("""{"type":"message_delta","delta":{"stop_reason":"end_turn"}}""") +
                        frame("""{"type":"message_stop"}""")
            }
            return ScriptedReply(200, body)
        }

        private fun frame(body: String): String = "data: $body\n\n"
    }

    private enum class Caller(val operation: RecoveryOperation) {
        COMPLETE(RecoveryOperation.COMPLETE),
        STRUCTURED(RecoveryOperation.STRUCTURED),
        STREAM(RecoveryOperation.STREAM),
        ;

        suspend fun invoke(gateway: LlmGateway, policy: RecoveryPolicy, output: MutableList<String>) {
            val config = CompletionConfig(temperature = 0.25, maxTokens = 127, recovery = policy)
            val messages = RecoveryTestFixtures.messages
            when (this) {
                COMPLETE -> output += gateway.complete("test-model", messages, config = config).content.orEmpty()

                STRUCTURED ->
                    output +=
                        requireNotNull(
                            gateway.completeJsonResponse("test-model", messages, RecoveryTestFixtures.schema, config).structuredJson,
                        ).toString()

                STREAM -> gateway.stream("test-model", messages, config = config).collect { output += it.toString() }
            }
        }
    }

    @Test
    fun disabledTransportLeavesQueuedStreamSuccessUnused(): Unit = runTest {
        scenario(Adapter.OMLX, Caller.STREAM, transport = true, enabled = false)
    }

    @Test
    fun transportToggleControlsEligibilityAcrossPublicEntrypoints(): Unit = runTest {
        differentialMatrix(transport = true)
    }

    @Test
    fun statusSetControlsEligibilityAcrossPublicEntrypoints(): Unit = runTest {
        differentialMatrix(transport = false)
    }

    private suspend fun differentialMatrix(transport: Boolean) {
        for (adapter in Adapter.entries) {
            for (caller in Caller.entries) {
                val enabledRequest = scenario(adapter, caller, transport, enabled = true)
                val disabledRequest = scenario(adapter, caller, transport, enabled = false)
                assertContentEquals(enabledRequest, disabledRequest, "$adapter $caller transport=$transport")
            }
        }
    }

    private class Evidence {
        val events = mutableListOf<RecoveryEvent>()
        val wires = mutableListOf<RecoveryWire>()
        val sleeps = mutableListOf<Long>()
        val admissions = mutableListOf<RecoveryFailure>()
        val output = mutableListOf<String>()
        val successUsed = AtomicBoolean(false)

        fun policy(transport: Boolean, enabled: Boolean): RecoveryPolicy = RecoveryPolicy(
            maxAttempts = 2,
            retryTransport = if (transport) enabled else true,
            retryableStatuses = if (transport || enabled) setOf(503) else emptySet(),
            admission = {
                admissions += it
                true
            },
            sleep = { sleeps += it },
            clock = { 0 },
            monotonicClock = { 0 },
            jitter = { it },
            observer = { events += it },
            capture = { wires += it },
        )
    }

    private suspend fun scenario(adapter: Adapter, caller: Caller, transport: Boolean, enabled: Boolean): ByteArray {
        val evidence = Evidence()
        val success = adapter.success(caller).copy(beforeReply = { evidence.successUsed.set(true) })
        // Identical initial failure and success for both controls; only eligibility changes.
        val first = if (transport) ScriptedReply(0, "") else ScriptedReply(503, "private-busy")
        RecoveryScriptedTransport(listOf(first, success)).use { server ->
            adapter.gateway(server.url, engine = server.engine).use { gateway ->
                invoke(caller, gateway.value, evidence, transport, enabled)
            }
            val retained = requireNotNull(evidence.events.single { it.stage == RecoveryStage.FAILED }.failure)
            assertFailure(retained, caller, transport, enabled)
            assertLifecycle(evidence.events, retained, enabled)
            assertEquals(if (enabled) listOf(retained) else emptyList(), evidence.admissions)
            assertEquals(if (enabled) listOf(100L) else emptyList(), evidence.sleeps)
            assertEquals(enabled, evidence.successUsed.get(), "queued success consumption")
            assertCaptures(server, evidence, success, transport, enabled)
            assertRequest(server, adapter)
            assertFalse(Json.encodeToString(evidence.events.map { it.summary() }).contains("private"))
            return server.requests.first()
        }
    }

    private suspend fun invoke(caller: Caller, gateway: LlmGateway, evidence: Evidence, transport: Boolean, enabled: Boolean) {
        val policy = evidence.policy(transport, enabled)
        if (enabled) {
            caller.invoke(gateway, policy, evidence.output)
            assertTrue(evidence.output.joinToString("").contains("ok"))
        } else {
            val terminal = assertFailsWith<RecoveryException> { caller.invoke(gateway, policy, evidence.output) }
            assertEquals(RecoveryReason.HTTP_PERMANENT, terminal.reason)
            assertEquals(1, terminal.wireAttempts)
            assertEquals(evidence.events.last().failures, terminal.failures)
            assertTrue(evidence.output.isEmpty())
        }
    }

    private fun assertCaptures(
        server: RecoveryScriptedTransport,
        evidence: Evidence,
        success: ScriptedReply,
        transport: Boolean,
        enabled: Boolean,
    ) {
        val wires = evidence.wires
        assertEquals(if (enabled) 2 else 1, server.requests.size)
        val sends = wires.filter { it.inspectResponse() == null }
        assertEquals(if (enabled) 2 else 1, sends.size)
        sends.zip(server.requests).forEach { (wire, request) -> assertContentEquals(request, wire.inspectRequest()) }
        assertEquals(evidence.events.filter { it.stage == RecoveryStage.STARTED }.map { it.identity }, sends.map { it.identity })
        assertTrue(wires.all { it.identity == sends[it.identity.attemptNumber - 1].identity })
        if (enabled) {
            assertContentEquals(server.requests[0], server.requests[1])
            assertEquals(server.requestLines[0], server.requestLines[1])
            assertEquals(server.requestHeaders[0], server.requestHeaders[1])
            assertContentEquals(success.body.encodeToByteArray(), wires.last { it.complete }.inspectResponse())
        }
        if (!transport) {
            assertContentEquals("private-busy".encodeToByteArray(), wires.first { it.complete }.inspectResponse())
        }
    }

    private fun assertRequest(server: RecoveryScriptedTransport, adapter: Adapter) {
        val path = when (adapter) {
            Adapter.OPENAI -> "/chat/completions"
            Adapter.OMLX -> "/v1/chat/completions"
            Adapter.ANTHROPIC -> "/v1/messages"
            Adapter.OLLAMA -> "/api/chat"
        }
        assertEquals("POST $path HTTP/1.1", server.requestLines.first())
        val body = server.requests.first().decodeToString()
        assertTrue(body.contains("test-model"))
        assertTrue(body.contains("private-request"))
        assertTrue(body.contains("prior-assistant"))
    }

    private fun assertFailure(failure: RecoveryFailure, caller: Caller, transport: Boolean, enabled: Boolean) {
        assertEquals(caller.operation, failure.operation)
        assertEquals(if (transport) RecoveryCategory.TRANSPORT else RecoveryCategory.HTTP, failure.category)
        assertEquals(if (transport) null else 503, failure.status)
        val reason = if (!enabled) {
            RecoveryReason.HTTP_PERMANENT
        } else if (transport) {
            RecoveryReason.TRANSPORT
        } else {
            RecoveryReason.HTTP_TRANSIENT
        }
        assertEquals(reason, failure.reason)
        assertEquals(enabled, failure.eligible)
        assertTrue(failure.wireSent)
        assertEquals(1, failure.identity.attemptNumber)
        assertContentEquals(if (transport) byteArrayOf() else "private-busy".encodeToByteArray(), failure.inspectBytes())
        assertEquals(if (transport) 0L else "private-busy".encodeToByteArray().size.toLong(), failure.progress.rawBytes)
        assertEquals(!transport, failure.progress.headersReceived)
        assertFalse(failure.progress.semanticObserved)
        assertFalse(failure.progress.semanticDelivered)
        assertFalse(failure.progress.replayUnsafe)
        if (transport) assertIs<java.io.IOException>(failure.inspectCause())
    }

    private fun assertLifecycle(events: List<RecoveryEvent>, retained: RecoveryFailure, enabled: Boolean) {
        val prefix = listOf(RecoveryStage.STARTED, RecoveryStage.FAILED)
        if (!enabled) {
            assertEquals(prefix + RecoveryStage.EXHAUSTED, events.map { it.stage })
            assertTrue(events.all { it.identity == retained.identity })
            assertSame(retained, events.last().failure)
        } else {
            assertEquals(
                prefix + listOf(RecoveryStage.ADMISSION_PENDING, RecoveryStage.ADMITTED, RecoveryStage.SCHEDULED, RecoveryStage.STARTED),
                events.take(6).map { it.stage },
            )
            assertEquals(RecoveryStage.SUCCEEDED, events.last().stage)
            assertTrue(events.drop(6).dropLast(1).all { it.stage == RecoveryStage.PROGRESS || it.stage == RecoveryStage.METRICS })
            val starts = events.filter { it.stage == RecoveryStage.STARTED }.map { it.identity }
            assertEquals(listOf(1, 2), starts.map { it.attemptNumber })
            assertEquals(starts[0].logicalId, starts[1].logicalId)
            assertNotEquals(starts[0].attemptId, starts[1].attemptId)
            assertTrue(events.take(5).all { it.identity == retained.identity })
            assertTrue(events.drop(5).all { it.identity == starts[1] })
        }
        assertEquals(listOf(retained), events.last().failures)
        assertEquals(if (enabled) 2 else 1, events.last().wireAttempts)
    }
}
