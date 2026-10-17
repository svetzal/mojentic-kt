package com.mojentic.anthropic

import com.mojentic.llm.ChatSession
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.StreamEvent
import com.mojentic.llm.recovery.RecoveryAcceptance
import com.mojentic.llm.recovery.RecoveryCategory
import com.mojentic.llm.recovery.RecoveryEvent
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryFailure
import com.mojentic.llm.recovery.RecoveryPhase
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryReason
import com.mojentic.llm.recovery.RecoveryStage
import com.mojentic.llm.recovery.RecoveryWire
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdmissionAmendmentHttpTest {
    @Test
    fun remoteNoHookRetriesAcrossPublicEntrypoints(): Unit = runBlocking {
        for (entry in Entry.entries) {
            for (status in listOf(0, 500, 502, 504)) {
                val events = mutableListOf<RecoveryEvent>()
                val wires = mutableListOf<RecoveryWire>()
                RecoveryScriptedServer(listOf(ScriptedReply(status, "private-failure"), entry.success())).use { server ->
                    val gateway = AnthropicGateway("private-key", server.url)
                    try {
                        entry.call(
                            gateway,
                            RecoveryPolicy(maxAttempts = 2, jitter = { 0 }, observer = { events += it }, capture = { wires += it }),
                        )
                        assertEquals(2, server.requests.size)
                        assertContentEquals(server.requests[0], server.requests[1])
                        val sent = wires.filter { it.inspectResponse() == null }
                        assertEquals(listOf(1, 2), sent.map { it.identity.attemptNumber })
                        assertEquals(1, sent.map { it.identity.logicalId }.distinct().size)
                        assertEquals(2, sent.map { it.identity.attemptId }.distinct().size)
                        sent.zip(server.requests).forEach { (wire, request) -> assertContentEquals(request, wire.inspectRequest()) }
                        assertEquals(RecoveryStage.SUCCEEDED, events.last().stage)
                        assertFalse(events.any { it.stage == RecoveryStage.ADMISSION_PENDING })
                        val retained = events.last().failures.single()
                        assertSafe(retained, status)
                        assertEquals("anthropic", retained.provider)
                        assertEquals(entry.operation, retained.operation)
                        assertTrue(events.filter { it.stage == RecoveryStage.SCHEDULED }.all { it.delayMillis == 0L })
                        assertFalse("private" in Json.encodeToString(events.last().summary()))
                    } finally {
                        gateway.close()
                    }
                }
            }
        }
    }

    @Test
    fun pendingRejectUndecidedAndCancellationPreventQueuedSentinel(): Unit = runBlocking {
        for (entry in Entry.entries) {
            for (outcome in listOf("reject", "undecided", "cancel")) {
                val pending = CompletableDeferred<RecoveryFailure>()
                val decision = CompletableDeferred<Boolean?>()
                val events = mutableListOf<RecoveryEvent>()
                RecoveryScriptedServer(listOf(ScriptedReply(503, "private-failure"), entry.success())).use { server ->
                    val gateway = AnthropicGateway("private-key", server.url)
                    try {
                        val call = async {
                            runCatching {
                                entry.call(
                                    gateway,
                                    RecoveryPolicy(
                                        maxAttempts = 3,
                                        budgetMillis = 60_000,
                                        admission = {
                                            pending.complete(it)
                                            decision.await()
                                        },
                                        observer = { events += it },
                                    ),
                                )
                            }
                        }
                        val original = withTimeout(5000) { pending.await() }
                        assertEquals(1, server.requests.size)
                        assertFalse(call.isCompleted)
                        if (outcome == "cancel") {
                            call.cancelAndJoin()
                            assertEquals(RecoveryStage.CANCELLED, events.last().stage)
                            decision.complete(true)
                        } else {
                            decision.complete(if (outcome == "reject") false else null)
                            val failure = withTimeout(5000) { call.await() }.exceptionOrNull() as RecoveryException
                            assertEquals(
                                if (outcome == "reject") RecoveryReason.ADMISSION_REJECTED else RecoveryReason.ADMISSION_UNDECIDED,
                                failure.reason,
                            )
                            assertEquals(listOf(original), failure.failures)
                            assertEquals(1, failure.wireAttempts)
                            assertEquals(outcome == "reject", events.any { it.stage == RecoveryStage.REJECTED })
                        }
                        assertEquals(1, server.requests.size)
                        assertEquals(listOf(original), events.last().failures)
                        assertFalse(events.any { it.stage == RecoveryStage.ADMITTED || it.stage == RecoveryStage.SCHEDULED })
                        assertSafe(original, 503)
                        assertFalse("private" in Json.encodeToString(events.last().summary()))
                    } finally {
                        gateway.close()
                    }
                }
            }
        }
    }

    @Test
    fun defaultOneAttemptPreventsQueuedSentinel(): Unit = runBlocking {
        for (entry in Entry.entries) {
            RecoveryScriptedServer(listOf(ScriptedReply(503, "private-failure"), entry.success())).use { server ->
                val gateway = AnthropicGateway("private-key", server.url)
                try {
                    val failure = assertFailsWith<RecoveryException> { entry.call(gateway, RecoveryPolicy()) }
                    assertEquals(RecoveryReason.EXHAUSTED, failure.reason)
                    assertEquals(1, failure.wireAttempts)
                    assertEquals(1, server.requests.size)
                } finally {
                    gateway.close()
                }
            }
        }
    }

    @Test
    fun deliveredContentWithoutHookPreventsReplayThroughBrokerAndSession(): Unit = runBlocking {
        for (entry in listOf(Entry.STREAM, Entry.BROKER_STREAM, Entry.SESSION_STREAM)) {
            val body = "data: " + """{"type":"message_start","message":{"content":[]}}""" + "\n\n" +
                "data: " + """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""" + "\n\n" +
                "data: " + """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"private-partial"}}""" +
                "\n\n"
            val events = mutableListOf<RecoveryEvent>()
            RecoveryScriptedServer(listOf(ScriptedReply(200, body), entry.success())).use { server ->
                val gateway = AnthropicGateway("private-key", server.url)
                try {
                    val failure = assertFailsWith<RecoveryException> {
                        entry.call(gateway, RecoveryPolicy(maxAttempts = 2, observer = { events += it }))
                    }
                    assertEquals(RecoveryReason.PARTIAL_SUCCESS, failure.reason)
                    val retained = failure.failures.single()
                    assertTrue(retained.progress.semanticDelivered)
                    assertTrue(retained.progress.replayUnsafe)
                    assertEquals(RecoveryStage.INTERRUPTED, events.last().stage)
                    assertEquals(1, server.requests.size)
                    assertFalse("private" in Json.encodeToString(events.last().summary()))
                } finally {
                    gateway.close()
                }
            }
        }
    }

    private fun assertSafe(failure: RecoveryFailure, status: Int) {
        assertEquals(if (status == 0) null else status, failure.status)
        assertEquals(if (status == 0) RecoveryCategory.TRANSPORT else RecoveryCategory.HTTP, failure.category)
        assertEquals(if (status == 0) RecoveryReason.TRANSPORT else RecoveryReason.HTTP_TRANSIENT, failure.reason)
        assertEquals(if (status == 0) RecoveryPhase.UNKNOWN else RecoveryPhase.RECEIVING, failure.phase)
        assertEquals(RecoveryAcceptance.UNKNOWN, failure.acceptance)
        assertEquals(2, failure.nextAttemptNumber)
        assertTrue(failure.eligible)
        assertTrue(failure.wireSent)
        assertFalse(failure.progress.semanticObserved)
        assertFalse(failure.progress.semanticDelivered)
    }

    private enum class Entry {
        ORDINARY,
        STRUCTURED,
        STREAM,
        BROKER,
        BROKER_STRUCTURED,
        SESSION,
        BROKER_STREAM,
        SESSION_STREAM,
        ;

        val operation: String get() = when (this) {
            STRUCTURED, BROKER_STRUCTURED -> "structured"
            STREAM, BROKER_STREAM, SESSION_STREAM -> "stream"
            else -> "complete"
        }

        fun success(): ScriptedReply = ScriptedReply(
            200,
            when (this) {
                STREAM, BROKER_STREAM, SESSION_STREAM ->
                    "data: " + """{"type":"message_start","message":{"content":[]}}""" + "\n\n" +
                        "data: " + """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""" + "\n\n" +
                        "data: " + """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"ok"}}""" +
                        "\n\ndata: " + """{"type":"content_block_stop","index":0}""" +
                        "\n\ndata: " + """{"type":"message_delta","delta":{"stop_reason":"end_turn"}}""" +
                        "\n\ndata: " + """{"type":"message_stop"}""" + "\n\n"

                STRUCTURED, BROKER_STRUCTURED ->
                    """{"content":[{"type":"tool_use","id":"t","name":"respond_in_json","input":{"value":"ok"}}]}"""

                else -> """{"content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn"}"""
            },
        )

        suspend fun call(gateway: AnthropicGateway, policy: RecoveryPolicy) {
            val config = CompletionConfig(recovery = policy)
            val messages = listOf(LlmMessage.user("private-request"))
            val broker = LlmBroker(gateway)
            val session = ChatSession(broker, "claude", config = config)
            val content = StringBuilder()
            when (this) {
                ORDINARY -> assertEquals("ok", gateway.complete("claude", messages, config = config).content)

                STRUCTURED -> {
                    val schema = buildJsonObject { put("type", "object") }
                    assertEquals("\"ok\"", gateway.completeJson("claude", messages, schema, config)["value"].toString())
                }

                STREAM -> gateway.stream("claude", messages, config = config).collect {
                    if (it is GatewayStreamEvent.Content) content.append(it.text)
                }

                BROKER -> assertEquals("ok", broker.complete("claude", messages, config = config).content)

                BROKER_STRUCTURED -> assertEquals("\"ok\"", broker.completeJson<JsonObject>("claude", messages, config)["value"].toString())

                SESSION -> assertEquals("ok", session.send("private-request").content)

                BROKER_STREAM -> broker.stream("claude", messages, config = config).collect {
                    if (it is StreamEvent.TextChunk) content.append(it.text)
                }

                SESSION_STREAM -> session.stream("private-request").collect {
                    if (it is StreamEvent.TextChunk) content.append(it.text)
                }
            }
            if (this in listOf(STREAM, BROKER_STREAM, SESSION_STREAM)) assertEquals("ok", content.toString())
        }
    }
}
