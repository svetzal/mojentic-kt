package com.mojentic.omlx

import com.mojentic.llm.ChatSession
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.StreamErrorReason
import com.mojentic.llm.StreamEvent
import com.mojentic.llm.StreamEventsGateway
import com.mojentic.llm.recovery.RecoveryAcceptance
import com.mojentic.llm.recovery.RecoveryCategory
import com.mojentic.llm.recovery.RecoveryEvent
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryFailure
import com.mojentic.llm.recovery.RecoveryLifecycleSummary
import com.mojentic.llm.recovery.RecoveryOperation
import com.mojentic.llm.recovery.RecoveryPhase
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryProvider
import com.mojentic.llm.recovery.RecoveryReason
import com.mojentic.llm.recovery.RecoveryReport
import com.mojentic.llm.recovery.RecoveryStage
import com.mojentic.llm.recovery.RecoveryWire
import com.mojentic.llm.tools.LlmTool
import com.mojentic.omlx.RecoveryTestFixtures.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdmissionAmendmentHttpTest {
    @Test
    fun typedFailuresPreserveLegacySerializedReports(): Unit = runBlocking {
        for (provider in Provider.entries) {
            for (entry in Entry.entries) {
                RecoveryScriptedServer(listOf(ScriptedReply(400, "private-error"))).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        val events = mutableListOf<RecoveryEvent>()
                        val policy = RecoveryPolicy(observer = { events += it })
                        val failure = assertFailsWith<RecoveryException> { entry.call(gateway, policy) }
                        val expectedProvider = when (provider) {
                            Provider.OLLAMA -> RecoveryProvider.OLLAMA
                            Provider.OMLX -> RecoveryProvider.OMLX
                            Provider.OPENAI -> RecoveryProvider.OPENAI
                        }
                        val providerName = provider.name.lowercase()
                        val operation = when (entry.operation) {
                            "complete" -> RecoveryOperation.COMPLETE
                            "structured" -> RecoveryOperation.STRUCTURED
                            "stream" -> RecoveryOperation.STREAM
                            "streamEvents" -> RecoveryOperation.STREAM_EVENTS
                            else -> error("Unknown test operation")
                        }
                        val attempt = failure.failures.single()
                        assertEquals(expectedProvider, attempt.provider)
                        assertEquals(operation, attempt.operation)
                        assertEquals(400, attempt.status)
                        assertEquals(RecoveryReason.HTTP_PERMANENT, attempt.reason)
                        assertSerializedReports(failure, events, providerName, entry.operation)
                        assertEquals(1, server.requests.size)
                    }
                }
            }
        }
    }

    private fun assertSerializedReports(
        failure: RecoveryException,
        events: List<RecoveryEvent>,
        providerName: String,
        operationName: String,
    ) {
        val attempt = failure.failures.single()
        val report = failure.summary()
        val encoded = Json.encodeToString(report)
        assertEquals(report, Json.decodeFromString<RecoveryReport>(encoded))
        val fields = Json.parseToJsonElement(encoded).jsonObject.getValue("failures").jsonArray.single().jsonObject
        assertEquals(providerName, fields.getValue("provider").jsonPrimitive.content)
        assertEquals(operationName, fields.getValue("operation").jsonPrimitive.content)
        assertFalse("providerCode" in fields)
        assertFalse("providerRequestId" in fields)
        assertFalse("private" in encoded)
        assertEquals(
            listOf(RecoveryStage.STARTED, RecoveryStage.FAILED, RecoveryStage.EXHAUSTED),
            events.map { it.stage },
        )
        assertTrue(attempt.toString().startsWith("RecoveryFailure(provider=$providerName,"))
        for (event in events.filter { it.failure != null }) {
            assertEquals(attempt.provider, event.failure?.provider)
            assertEquals(attempt.operation, event.failure?.operation)
            val summary = event.summary()
            assertEquals(report.failures, summary.failures)
            assertEquals(summary, Json.decodeFromString<RecoveryLifecycleSummary>(Json.encodeToString(summary)))
        }
        assertFailsWith<SerializationException> {
            Json.decodeFromString<RecoveryReport>(
                encoded.replace("\"provider\":\"$providerName\"", "\"provider\":\"future\""),
            )
        }
        assertFailsWith<SerializationException> {
            Json.decodeFromString<RecoveryReport>(
                encoded.replace("\"operation\":\"${operationName}\"", "\"operation\":\"future\""),
            )
        }
    }

    @Test
    fun remoteRetryWithoutHookPreservesExactPayloadAndIdentity(): Unit = runBlocking {
        val events = mutableListOf<RecoveryEvent>()
        RecoveryScriptedServer(
            listOf(ScriptedReply(502, "private-failure"), RecoveryTestFixtures.Provider.OPENAI.success(false)),
        ).use { server ->
            RecoveryTestFixtures.Provider.OPENAI.gateway(server.url).use { gateway ->
                val result = gateway.value.complete(
                    "test-model",
                    RecoveryTestFixtures.messages,
                    config = CompletionConfig(recovery = RecoveryPolicy(maxAttempts = 2, jitter = { 0 }, observer = { events += it })),
                )
                assertEquals("ok", result.content)
                assertEquals(2, server.requests.size)
                assertContentEquals(server.requests[0], server.requests[1])
                val started = events.filter { it.stage == RecoveryStage.STARTED }
                assertEquals(listOf(1, 2), started.map { it.identity.attemptNumber })
                assertEquals(1, started.map { it.identity.logicalId }.distinct().size)
                assertEquals(2, started.map { it.identity.attemptId }.distinct().size)
                assertEquals(1, events.last().failures.size)
                assertFalse("private" in events.toString())
            }
        }
    }

    @Test
    fun noHookDistinguishesRemoteAndLocalAcrossPublicEntrypoints(): Unit = runBlocking {
        for (provider in Provider.entries) {
            for (entry in Entry.entries) {
                for (status in listOf(0, 500, 502, 504)) {
                    val events = mutableListOf<RecoveryEvent>()
                    val wires = mutableListOf<RecoveryWire>()
                    RecoveryScriptedServer(listOf(ScriptedReply(status, "private-failure"), entry.success(provider))).use { server ->
                        provider.gateway(server.url).use { gateway ->
                            val policy = RecoveryPolicy(
                                maxAttempts = 2,
                                jitter = { 0 },
                                observer = { events += it },
                                capture = { wires += it },
                            )
                            if (provider == Provider.OPENAI) {
                                entry.call(gateway, policy)
                                assertEquals(2, server.requests.size)
                                assertContentEquals(server.requests[0], server.requests[1])
                                val sent = wires.filter { it.inspectResponse() == null }
                                assertEquals(listOf(1, 2), sent.map { it.identity.attemptNumber })
                                assertEquals(1, sent.map { it.identity.logicalId }.distinct().size)
                                assertEquals(2, sent.map { it.identity.attemptId }.distinct().size)
                                sent.zip(server.requests).forEach { (wire, request) -> assertContentEquals(request, wire.inspectRequest()) }
                                assertEquals(RecoveryStage.SUCCEEDED, events.last().stage)
                            } else {
                                val failure = assertFailsWith<RecoveryException> { entry.call(gateway, policy) }
                                assertEquals(RecoveryReason.ADMISSION_REQUIRED, failure.reason)
                                assertEquals(1, server.requests.size)
                                assertEquals(1, failure.wireAttempts)
                                assertEquals(listOf(1), failure.failures.map { it.identity.attemptNumber })
                            }
                            assertFalse(events.any { it.stage == RecoveryStage.ADMISSION_PENDING })
                            val retained = events.last().failures.single()
                            assertSafeFailure(retained, status)
                            assertEquals(RecoveryProvider.valueOf(provider.name), retained.provider)
                            assertEquals(Json.decodeFromString<RecoveryOperation>(Json.encodeToString(entry.operation)), retained.operation)
                            assertTrue(events.filter { it.stage == RecoveryStage.SCHEDULED }.all { it.delayMillis == 0L })
                            assertFalse("private" in Json.encodeToString(events.last().summary()))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun pendingRejectUndecidedAndCancellationNeverConsumeQueuedSentinel(): Unit = runBlocking {
        for (provider in Provider.entries) {
            for (entry in Entry.entries) {
                for (outcome in listOf("reject", "undecided", "cancel")) {
                    val pending = CompletableDeferred<RecoveryFailure>()
                    val decision = CompletableDeferred<Boolean?>()
                    val events = mutableListOf<RecoveryEvent>()
                    RecoveryScriptedServer(listOf(ScriptedReply(503, "private-failure"), entry.success(provider))).use { server ->
                        provider.gateway(server.url).use { gateway ->
                            val call = async {
                                runCatching {
                                    entry.call(
                                        gateway,
                                        RecoveryPolicy(
                                            maxAttempts = 3,
                                            budgetMillis = 60_000,
                                            admission = { failure ->
                                                pending.complete(failure)
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
                            assertEquals(2, original.nextAttemptNumber)
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
                            assertSafeFailure(original, 503)
                            assertFalse("private" in Json.encodeToString(events.last().summary()))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun undecidedWithoutBudgetIsDistinctFromRejection(): Unit = runBlocking {
        RecoveryScriptedServer(listOf(ScriptedReply(503, "private-failure"), Provider.OPENAI.success(false))).use { server ->
            Provider.OPENAI.gateway(server.url).use { gateway ->
                val failure = assertFailsWith<RecoveryException> {
                    Entry.ORDINARY.call(gateway, RecoveryPolicy(maxAttempts = 2, admission = { null }))
                }
                assertEquals(RecoveryReason.ADMISSION_UNDECIDED, failure.reason)
                assertEquals(1, server.requests.size)
            }
        }
    }

    @Test
    fun defaultOneAttemptStillPreventsQueuedSentinel(): Unit = runBlocking {
        for (provider in Provider.entries) {
            for (entry in Entry.entries) {
                RecoveryScriptedServer(listOf(ScriptedReply(503, "private-failure"), entry.success(provider))).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        val failure = assertFailsWith<RecoveryException> { entry.call(gateway, RecoveryPolicy()) }
                        assertEquals(RecoveryReason.EXHAUSTED, failure.reason)
                        assertEquals(1, failure.wireAttempts)
                        assertEquals(1, server.requests.size)
                    }
                }
            }
        }
    }

    @Test
    fun semanticDeliveryWithoutHookPreventsReplayThroughBrokerAndSession(): Unit = runBlocking {
        for (provider in Provider.entries) {
            for (entry in Entry.entries.filter { it.streaming }) {
                val body = if (provider == Provider.OLLAMA) {
                    """{"message":{"role":"assistant","content":"private-partial"},"done":false}""" + "\n"
                } else {
                    "data: " + """{"choices":[{"delta":{"content":"private-partial"}}]}""" + "\n\n"
                }
                val events = mutableListOf<RecoveryEvent>()
                RecoveryScriptedServer(listOf(ScriptedReply(200, body), entry.success(provider))).use { server ->
                    provider.gateway(server.url).use { gateway ->
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
                    }
                }
            }
        }
    }

    private fun assertSafeFailure(failure: RecoveryFailure, status: Int) {
        assertEquals(if (status == 0) null else status, failure.status)
        assertEquals(if (status == 0) RecoveryCategory.TRANSPORT else RecoveryCategory.HTTP, failure.category)
        assertEquals(if (status == 0) RecoveryReason.TRANSPORT else RecoveryReason.HTTP_TRANSIENT, failure.reason)
        assertEquals(if (status == 0) RecoveryPhase.UNKNOWN else RecoveryPhase.RECEIVING, failure.phase)
        assertEquals(RecoveryAcceptance.UNKNOWN, failure.acceptance)
        assertTrue(failure.eligible)
        assertTrue(failure.wireSent)
        assertFalse(failure.progress.semanticDelivered)
        assertFalse(failure.progress.semanticObserved)
        assertEquals(2, failure.nextAttemptNumber)
    }

    internal enum class Entry {
        ORDINARY,
        STRUCTURED,
        STRUCTURED_RESPONSE,
        BROKER_RESPONSE,
        STREAM,
        EVENTS,
        BROKER_EVENTS,
        BROKER,
        BROKER_STRUCTURED,
        SESSION,
        BROKER_STREAM,
        SESSION_STREAM,
        ;

        val streaming: Boolean get() = this in listOf(STREAM, EVENTS, BROKER_EVENTS, BROKER_STREAM, SESSION_STREAM)
        val structured: Boolean get() = this in listOf(STRUCTURED, STRUCTURED_RESPONSE, BROKER_STRUCTURED)
        val operation: String get() = when {
            this == EVENTS || this == BROKER_EVENTS -> "streamEvents"
            streaming -> "stream"
            structured -> "structured"
            else -> "complete"
        }

        fun success(provider: Provider): ScriptedReply = if (!streaming) {
            provider.success(structured)
        } else {
            ScriptedReply(
                200,
                if (provider == Provider.OLLAMA) {
                    """{"message":{"role":"assistant","content":"ok"},"done":true,"done_reason":"stop"}""" + "\n"
                } else {
                    "data: " + """{"choices":[{"delta":{"content":"ok"},"finish_reason":"stop"}]}""" +
                        "\n\ndata: [DONE]\n\n"
                },
            )
        }

        private suspend fun contentOf(source: Flow<*>): String {
            val content = StringBuilder()
            source.collect {
                when (it) {
                    is GatewayStreamEvent.Content -> content.append(it.text)
                    is StreamEvent.TextChunk -> content.append(it.text)
                    is CompletionStreamEvent.Content -> content.append(it.text)
                    is CompletionStreamEvent.Error -> throw (it.reason as StreamErrorReason.RequestFailed).cause
                }
            }
            return content.toString()
        }

        suspend fun call(gateway: RecoveryTestFixtures.OwnedGateway, policy: RecoveryPolicy, tools: List<LlmTool> = emptyList()) {
            val config = CompletionConfig(recovery = policy)
            val broker = LlmBroker(gateway.value)
            val session = ChatSession(broker, "test-model", tools = tools, config = config)
            when (this) {
                ORDINARY -> assertEquals(
                    "ok",
                    gateway.value.complete("test-model", RecoveryTestFixtures.messages, tools = tools, config = config).content,
                )

                STRUCTURED -> {
                    val response = gateway.value.completeJson(
                        "test-model",
                        RecoveryTestFixtures.messages,
                        RecoveryTestFixtures.schema,
                        config,
                    )
                    assertEquals("\"ok\"", response["value"].toString())
                }

                STRUCTURED_RESPONSE -> assertEquals(
                    Json.parseToJsonElement("""{"value":"ok"}"""),
                    gateway.value.completeJsonResponse(
                        "test-model",
                        RecoveryTestFixtures.messages,
                        RecoveryTestFixtures.schema,
                        config,
                    ).structuredJson,
                )

                BROKER_RESPONSE -> assertEquals(
                    "ok",
                    broker.generateResponse("test-model", RecoveryTestFixtures.messages, tools, config).content,
                )

                STREAM -> assertEquals(
                    "ok",
                    contentOf(gateway.value.stream("test-model", RecoveryTestFixtures.messages, tools = tools, config = config)),
                )

                EVENTS -> assertEquals(
                    "ok",
                    contentOf((gateway.value as StreamEventsGateway).streamEvents("test-model", RecoveryTestFixtures.messages, config)),
                )

                BROKER_EVENTS -> assertEquals(
                    "ok",
                    contentOf(broker.generateStreamEvents("test-model", RecoveryTestFixtures.messages, config)),
                )

                BROKER -> assertEquals(
                    "ok",
                    broker.complete("test-model", RecoveryTestFixtures.messages, tools = tools, config = config).content,
                )

                BROKER_STRUCTURED -> assertEquals(
                    "\"ok\"",
                    broker.completeJson<JsonObject>("test-model", RecoveryTestFixtures.messages, config)["value"].toString(),
                )

                SESSION -> assertEquals("ok", session.send("private-request").content)

                BROKER_STREAM -> assertEquals(
                    "ok",
                    contentOf(broker.stream("test-model", RecoveryTestFixtures.messages, tools = tools, config = config)),
                )

                SESSION_STREAM -> assertEquals("ok", contentOf(session.stream("private-request")))
            }
        }
    }
}
