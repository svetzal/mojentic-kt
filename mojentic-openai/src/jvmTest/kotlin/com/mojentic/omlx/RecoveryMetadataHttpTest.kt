package com.mojentic.omlx

import com.mojentic.llm.ChatSession
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.MessageRole
import com.mojentic.llm.StreamErrorReason
import com.mojentic.llm.StreamEventsGateway
import com.mojentic.llm.recovery.RecoveryEvent
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryFailure
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryStage
import com.mojentic.llm.recovery.RecoveryWire
import com.mojentic.omlx.RecoveryTestFixtures.Provider
import com.mojentic.omlx.RecoveryTestFixtures.call
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RecoveryMetadataHttpTest {
    @Test
    fun validatedMetadataSurvivesPublicHttpFailure(): Unit = runBlocking {
        val requestId = "req_123e4567-e89b-12d3-a456-426614174000"
        RecoveryScriptedServer(
            listOf(
                ScriptedReply(
                    503,
                    """{"error":{"code":"server_error","message":"private-response"}}""",
                    mapOf("X-Request-ID" to requestId),
                ),
                Provider.OPENAI.success(false),
            ),
        ).use { server ->
            Provider.OPENAI.gateway(server.url).use { gateway ->
                val error = assertFailsWith<RecoveryException> { call(gateway.value, false, RecoveryPolicy()) }
                assertEquals("server_error", error.failures.single().providerCode)
                assertEquals(requestId, error.failures.single().providerRequestId)
                assertEquals(1, server.requests.size)
            }
        }
    }

    @Test
    fun exactMetadataAndOrderedHistoriesAcrossAllPublicOperations(): Unit = runBlocking {
        matrix { provider, operation ->
            val events = mutableListOf<RecoveryEvent>()
            val wires = mutableListOf<RecoveryWire>()
            val replies = listOf(
                reply(503, "server_error", FIRST_ID),
                reply(429, "rate_limit_exceeded", SECOND_ID),
                provider.success(false),
            )
            RecoveryScriptedServer(replies).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val error = assertFailsWith<RecoveryException> {
                        invoke(
                            gateway,
                            operation,
                            RecoveryPolicy(
                                maxAttempts = 2,
                                admission = { true },
                                sleep = {},
                                observer = { events += it },
                                capture = { wires += it },
                            ),
                        )
                    }
                    assertEquals(2, server.requests.size)
                    assertContentEquals(server.requests[0], server.requests[1])
                    assertEquals(listOf("server_error", "rate_limit_exceeded"), error.failures.map { it.providerCode })
                    assertEquals(listOf(FIRST_ID, SECOND_ID), error.failures.map { it.providerRequestId })
                    assertEquals(listOf(1, 2), error.failures.map { it.identity.attemptNumber })
                    assertEquals(error.failures[0].identity.logicalId, error.failures[1].identity.logicalId)
                    assertNotEquals(error.failures[0].identity.attemptId, error.failures[1].identity.attemptId)
                    error.failures.zip(replies).forEach { (failure, response) ->
                        assertPrivateEvidence(failure, response)
                        assertEquals(failure.providerCode, failure.summary().providerCode)
                        assertEquals(failure.providerRequestId, failure.summary().providerRequestId)
                        assertEquals(failure.summary(), failure.summary().copy())
                        assertEquals(failure.summary(), Json.decodeFromString(Json.encodeToString(failure.summary())))
                        val capture = wires.last { it.identity == failure.identity && it.complete }
                        assertContentEquals(response.body.encodeToByteArray(), capture.inspectResponse())
                        assertContentEquals(server.requests[failure.identity.attemptNumber - 1], capture.inspectRequest())
                    }
                    assertEquals(listOf(1, 2), events.filter { it.stage == RecoveryStage.FAILED }.map { it.failures.size })
                    assertEquals(error.failures, events.last().failures)
                    val safe = Json.encodeToString(error.summary()) + events.joinToString { Json.encodeToString(it.summary()) }
                    assertTrue(FIRST_ID in safe && SECOND_ID in safe)
                    assertTrue("server_error" in safe && "rate_limit_exceeded" in safe)
                    assertSafe(safe + error.toString() + error.failures + events)
                    assertNull(error.cause)
                }
            }
        }
    }

    @Test
    fun unsafeMetadataIsOmittedButExactEvidenceRemainsPrivate(): Unit = runBlocking {
        matrix { provider, operation ->
            val cases = listOf(
                reply(401, "unknown_provider_code", "unknown-request-id"),
                reply(400, "server_error ", "req_bad-uuid"),
                reply(400, "credential-secret", "credential-secret"),
                reply(400, "payload-secret", "payload-secret"),
                reply(400, "server_error", FIRST_ID),
                reply(400, "server_error", "req_$FIRST_ID"),
                ScriptedReply(400, "{malformed-private-response", mapOf("X-Request-ID" to "")),
                ScriptedReply(400, """{"error":{"code":123}}""", mapOf("X-Request-ID" to "$FIRST_ID,$SECOND_ID")),
            )
            cases.forEachIndexed { index, response ->
                val events = mutableListOf<RecoveryEvent>()
                RecoveryScriptedServer(listOf(response, provider.success(false))).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        val messages = if (index in 4..5) {
                            listOf(LlmMessage.user("payload-secret $FIRST_ID server_error"))
                        } else {
                            RecoveryTestFixtures.messages
                        }
                        val error = assertFailsWith<RecoveryException> {
                            invoke(
                                gateway,
                                operation,
                                RecoveryPolicy(maxAttempts = 2, admission = {
                                    error("permanent failure cannot resend")
                                }, observer = { events += it }),
                                messages,
                            )
                        }
                        val failure = error.failures.single()
                        assertNull(failure.providerCode)
                        assertNull(failure.providerRequestId)
                        assertPrivateEvidence(failure, response)
                        assertEquals(1, server.requests.size)
                        assertFalse(failure.eligible)
                        assertSafe(Json.encodeToString(error.summary()) + events.joinToString { Json.encodeToString(it.summary()) })
                        assertNull(failure.summary().providerCode)
                        assertNull(failure.summary().providerRequestId)
                    }
                }
            }
        }
    }

    @Test
    fun validatedRequestIdCannotEchoBearerCredential(): Unit = runBlocking {
        for (provider in listOf(Provider.OPENAI, Provider.OMLX)) {
            for (operation in 0..3) {
                RecoveryScriptedServer(listOf(reply(401, "invalid_api_key", "req_$FIRST_ID"), provider.success(false))).use { server ->
                    val gateway = if (provider == Provider.OPENAI) {
                        val value = com.mojentic.openai.OpenAIGateway(FIRST_ID, server.url)
                        RecoveryTestFixtures.OwnedGateway(value, value::close)
                    } else {
                        val value = OmlxGateway(server.url, apiKey = FIRST_ID)
                        RecoveryTestFixtures.OwnedGateway(value, value::close)
                    }
                    gateway.use {
                        val error = assertFailsWith<RecoveryException> { invoke(it, operation, RecoveryPolicy()) }
                        assertEquals("invalid_api_key", error.failures.single().providerCode)
                        assertNull(error.failures.single().providerRequestId)
                        assertEquals(
                            "req_$FIRST_ID",
                            error.failures.single().inspectHeaders().entries
                                .single { entry -> entry.key.equals("x-request-id", true) }.value.single(),
                        )
                        assertFalse(FIRST_ID in Json.encodeToString(error.summary()))
                        assertEquals(1, server.requests.size)
                    }
                }
            }
        }
    }

    @Test
    fun providerErrorEnvelopesRetainMetadataAtSuccessfulHttpStatus(): Unit = runBlocking {
        matrix { provider, operation ->
            val response = reply(200, "overloaded_error", SECOND_ID).let {
                if (operation >= 2 && provider != Provider.OLLAMA) it.copy(body = "data: ${it.body}\n\n") else it
            }
            RecoveryScriptedServer(listOf(response, provider.success(false))).use { server ->
                provider.gateway(server.url).use { gateway ->
                    val error = assertFailsWith<RecoveryException> { invoke(gateway, operation, RecoveryPolicy(maxAttempts = 2)) }
                    assertEquals("overloaded_error", error.failures.single().providerCode)
                    assertEquals(SECOND_ID, error.failures.single().providerRequestId)
                    assertPrivateEvidence(error.failures.single(), response)
                    assertEquals(1, server.requests.size)
                }
            }
        }
    }

    @Test
    fun brokerAndSessionPreserveMetadataAndOriginalEvidence(): Unit = runBlocking {
        for (provider in Provider.entries) {
            for (boundary in listOf("broker", "events", "session")) {
                val events = mutableListOf<RecoveryEvent>()
                val response = reply(401, "invalid_api_key", SECOND_ID)
                RecoveryScriptedServer(listOf(response, provider.success(false))).use { server ->
                    provider.gateway(server.url).use { gateway ->
                        val broker = LlmBroker(gateway.value)
                        val config = CompletionConfig(recovery = RecoveryPolicy(observer = { events += it }))
                        val session = ChatSession(broker, "test", systemPrompt = "system", config = config)
                        val before = session.messages()
                        val error = if (boundary == "events") {
                            val output = mutableListOf<CompletionStreamEvent>()
                            broker.generateStreamEvents("test", RecoveryTestFixtures.messages, config).collect { output += it }
                            val last = output.single() as CompletionStreamEvent.Error
                            (last.reason as StreamErrorReason.RequestFailed).cause as RecoveryException
                        } else {
                            assertFailsWith<RecoveryException> {
                                if (boundary == "session") {
                                    session.stream("payload-secret").collect {}
                                } else {
                                    broker.stream("test", RecoveryTestFixtures.messages, config = config).collect {}
                                }
                            }
                        }
                        val failure = error.failures.single()
                        assertSame(events.last().failures.single(), failure)
                        assertEquals("invalid_api_key", failure.providerCode)
                        assertEquals(SECOND_ID, failure.providerRequestId)
                        assertPrivateEvidence(failure, response)
                        assertEquals(before, session.messages())
                        assertEquals(1, server.requests.size)
                    }
                }
            }
        }
    }

    @Test
    fun ordinaryBrokerAndSessionPreserveHttpEvidenceAndRollback(): Unit = runBlocking {
        for (provider in Provider.entries) {
            for (sessionCall in listOf(false, true)) {
                for (permanent in listOf(true, false)) {
                    assertOrdinaryFailure(provider, sessionCall, permanent)
                }
            }
        }
    }

    private suspend fun assertOrdinaryFailure(provider: Provider, sessionCall: Boolean, permanent: Boolean) {
        val responses = if (permanent) {
            listOf(reply(401, "invalid_api_key", SECOND_ID))
        } else {
            listOf(reply(503, "server_error", FIRST_ID), reply(429, "rate_limit_exceeded", SECOND_ID))
        }
        val events = mutableListOf<RecoveryEvent>()
        val wires = mutableListOf<RecoveryWire>()
        val admitted = mutableListOf<RecoveryFailure>()
        var toolExecutions = 0
        val tools = listOf(RecoveryTestFixtures.countingTool { toolExecutions++ })
        val config = ordinaryConfig(permanent, events, wires, admitted)
        val seed = if (sessionCall) listOf(provider.success(false)) else emptyList()
        val success = provider.success(false)
        val sentinel = success.copy(body = success.body.replace("\"ok\"", "\"queued-success-sentinel\""))
        RecoveryScriptedServer(seed + responses + sentinel).use { server ->
            provider.gateway(server.url).use { gateway ->
                val broker = LlmBroker(gateway.value)
                val session = ChatSession(broker, "test-model", systemPrompt = "system", tools = tools, config = config)
                if (sessionCall) assertEquals("ok", session.send("prior-user").content)
                val before = session.messages()
                events.clear()
                wires.clear()
                val submitted = if (sessionCall) before + LlmMessage.user("private-request ⚙️") else RecoveryTestFixtures.messages
                val error = assertFailsWith<RecoveryException> {
                    if (sessionCall) {
                        session.send("private-request ⚙️")
                    } else {
                        broker.complete("test-model", submitted, tools, config)
                    }
                }
                val requests = server.requests.drop(seed.size)
                assertEquals(responses.size, requests.size, "$provider session=$sessionCall permanent=$permanent")
                requests.forEach { assertOrdinaryRequest(it, provider, submitted) }
                assertEquals(before, session.messages())
                assertEquals(0, toolExecutions)
                assertEquals(responses.size, error.wireAttempts)
                assertEquals(responses.size, error.failures.size)
                assertEquals(if (permanent) 0 else 1, admitted.size)
                if (permanent) {
                    assertEquals(listOf(RecoveryStage.STARTED, RecoveryStage.FAILED, RecoveryStage.EXHAUSTED), events.map { it.stage })
                }
                if (!permanent) {
                    assertSame(error.failures.first(), admitted.single())
                    assertContentEquals(requests[0], requests[1])
                }
                assertOrdinaryEvidence(error, responses, requests, events, wires)
            }
        }
    }

    private fun ordinaryConfig(
        permanent: Boolean,
        events: MutableList<RecoveryEvent>,
        wires: MutableList<RecoveryWire>,
        admitted: MutableList<RecoveryFailure>,
    ): CompletionConfig = CompletionConfig(
        temperature = 0.37,
        maxTokens = 29,
        numCtx = 2048,
        numPredict = 17,
        recovery = RecoveryPolicy(
            maxAttempts = 2,
            admission = {
                check(!permanent) { "permanent response must not invoke admission" }
                admitted += it
                true
            },
            sleep = {},
            jitter = { it },
            observer = { events += it },
            capture = { wires += it },
        ),
    )

    private fun assertOrdinaryRequest(bytes: ByteArray, provider: Provider, messages: List<LlmMessage>) {
        val request = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        assertEquals("test-model", request.getValue("model").jsonPrimitive.content)
        // The OpenAI-compatible encoder omits its false default; true always selects streaming.
        assertEquals("false", request["stream"]?.jsonPrimitive?.content ?: "false")
        val submitted = request.getValue("messages").jsonArray.map { it.jsonObject }
        assertEquals(messages.map { it.role.name.lowercase() }, submitted.map { it.getValue("role").jsonPrimitive.content })
        messages.zip(submitted).forEach { (message, encoded) ->
            val content = if (provider != Provider.OLLAMA && message.role == MessageRole.User) {
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("type", "text")
                            put("text", message.content)
                        },
                    ),
                )
            } else {
                JsonPrimitive(message.content)
            }
            assertEquals(content, encoded.getValue("content"))
        }
        assertNull(request["response_format"])
        assertNull(request["format"])
        assertNull(request["stream_options"])
        assertEquals(
            "count-tool",
            request.getValue("tools").jsonArray.single().jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content,
        )
        if (provider == Provider.OLLAMA) {
            val options = request.getValue("options").jsonObject
            assertEquals("0.37", options.getValue("temperature").jsonPrimitive.content)
            assertEquals("2048", options.getValue("num_ctx").jsonPrimitive.content)
            assertEquals("17", options.getValue("num_predict").jsonPrimitive.content)
        } else {
            assertEquals("0.37", request.getValue("temperature").jsonPrimitive.content)
            assertEquals("29", request.getValue("max_tokens").jsonPrimitive.content)
            assertNull(request["max_completion_tokens"])
        }
    }

    private fun assertOrdinaryEvidence(
        error: RecoveryException,
        responses: List<ScriptedReply>,
        requests: List<ByteArray>,
        events: List<RecoveryEvent>,
        wires: List<RecoveryWire>,
    ) {
        val failures = error.failures
        assertEquals((1..failures.size).toList(), failures.map { it.identity.attemptNumber })
        assertEquals(1, failures.map { it.identity.logicalId }.distinct().size)
        assertEquals(failures.size, failures.map { it.identity.attemptId }.distinct().size)
        assertEquals(RecoveryStage.EXHAUSTED, events.last().stage)
        assertSame(failures.last(), events.last().failure)
        val failedEvents = events.filter { it.stage == RecoveryStage.FAILED }
        assertEquals(failures.size, failedEvents.size)
        failures.forEachIndexed { index, failure ->
            val response = responses[index]
            assertEquals("complete", failure.operation)
            assertEquals(response.status, failure.status)
            val expectedCode = when (response.status) {
                401 -> "invalid_api_key"
                503 -> "server_error"
                else -> "rate_limit_exceeded"
            }
            assertEquals(expectedCode, failure.providerCode)
            assertEquals(response.headers.getValue("X-Request-ID"), failure.providerRequestId)
            assertEquals(response.status != 401, failure.eligible)
            assertPrivateEvidence(failure, response)
            val cause = failure.inspectCause()
            assertTrue(cause is IllegalStateException)
            assertEquals("HTTP failure", cause.message)
            assertSame(cause, failure.inspectBoundaryCause())
            val event = failedEvents[index]
            assertEquals(failure.identity, event.identity)
            assertSame(failure, event.failure)
            assertSame(cause, event.failure?.inspectCause())
            assertEquals(failures.take(index + 1), event.failures)
            event.failures.forEachIndexed { prior, retained -> assertSame(failures[prior], retained) }
            assertSame(failure, events.last().failures[index])
            val sent = wires.single { it.identity == failure.identity && it.inspectResponse() == null }
            assertContentEquals(requests[index], sent.inspectRequest())
            val received = wires.last { it.identity == failure.identity && it.complete }
            assertEquals(response.status, received.status)
            assertContentEquals(response.body.encodeToByteArray(), received.inspectResponse())
            assertContentEquals(requests[index], received.inspectRequest())
        }
        assertEquals(failures.map { it.identity }, events.filter { it.stage == RecoveryStage.STARTED }.map { it.identity })
        assertEquals(failures.map { it.summary() }, error.summary().failures)
    }

    @Test
    fun admittedSuccessPreservesFailureMetadataWithoutResendingAgain(): Unit = runBlocking {
        matrix { provider, operation ->
            val events = mutableListOf<RecoveryEvent>()
            val success = if (operation < 2) {
                provider.success(operation == 1)
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
            RecoveryScriptedServer(listOf(reply(503, "server_error", FIRST_ID), success, success)).use { server ->
                provider.gateway(server.url).use { gateway ->
                    invoke(
                        gateway,
                        operation,
                        RecoveryPolicy(
                            maxAttempts = 3,
                            admission = { true },
                            sleep = {},
                            observer = { events += it },
                        ),
                    )
                    assertEquals(2, server.requests.size)
                    assertContentEquals(server.requests[0], server.requests[1])
                    assertEquals(RecoveryStage.SUCCEEDED, events.last().stage)
                    assertEquals(FIRST_ID, events.last().failures.single().providerRequestId)
                    assertEquals("server_error", events.last().failures.single().summary().providerCode)
                    assertEquals(2, events.last().wireAttempts)
                }
            }
        }
    }

    private suspend fun matrix(block: suspend (Provider, Int) -> Unit) {
        for (provider in Provider.entries) for (operation in 0..3) block(provider, operation)
    }

    private suspend fun invoke(
        gateway: RecoveryTestFixtures.OwnedGateway,
        operation: Int,
        policy: RecoveryPolicy,
        messages: List<LlmMessage> = RecoveryTestFixtures.messages,
    ) {
        val config = CompletionConfig(recovery = policy)
        when (operation) {
            0 -> gateway.value.complete("test", messages, config = config)

            1 -> gateway.value.completeJsonResponse("test", messages, RecoveryTestFixtures.schema, config)

            2 -> gateway.value.stream("test", messages, config = config).collect {}

            else -> (gateway.value as StreamEventsGateway).streamEvents("test", messages, config).collect {
                if (it is CompletionStreamEvent.Error) throw (it.reason as StreamErrorReason.RequestFailed).cause
            }
        }
    }

    private fun reply(status: Int, code: String, id: String): ScriptedReply = ScriptedReply(
        status,
        """{"error":{"code":"$code","message":"private-response"}}""",
        mapOf("X-Request-ID" to id, "X-Private" to "credential-secret"),
    )

    private fun assertPrivateEvidence(failure: RecoveryFailure, response: ScriptedReply) {
        assertContentEquals(response.body.encodeToByteArray(), failure.inspectBytes())
        response.headers.forEach { (name, value) ->
            assertEquals(value, failure.inspectHeaders().entries.single { it.key.equals(name, true) }.value.single())
        }
        assertNotNull(failure.inspectCause())
        assertTrue(
            failure.inspectCause() is IllegalStateException || failure.inspectCause() is IllegalArgumentException ||
                failure.inspectCause() is com.mojentic.errors.LlmGatewayException ||
                failure.inspectCause() is com.mojentic.llm.recovery.RecoveryStreamException,
        )
    }

    private fun assertSafe(value: String) {
        for (secret in listOf("credential-secret", "payload-secret", "private-response", "unknown_provider_code", "unknown-request-id")) {
            assertFalse(secret in value, "unsafe value in safe output")
        }
    }

    private companion object {
        const val FIRST_ID = "123e4567-e89b-12d3-a456-426614174000"
        const val SECOND_ID = "req_123e4567-e89b-12d3-a456-426614174001"
    }
}
