package com.mojentic.anthropic

import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.ChatSession
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.recovery.RecoveryCancellationException
import com.mojentic.llm.recovery.RecoveryEvent
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryReason
import com.mojentic.llm.recovery.RecoveryRetryAfter
import com.mojentic.llm.recovery.RecoveryStage
import com.mojentic.llm.recovery.RecoveryStreamClosedException
import com.mojentic.llm.recovery.RecoveryWire
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AnthropicRecoveryHttpTest {
    private val messages = listOf(LlmMessage.system("system-private"), LlmMessage.user("payload-private"))
    private val success = """{"model":"actual-model","content":[{"type":"thinking","thinking":"reason"},""" +
        """{"type":"text","text":"ok"}],"stop_reason":"end_turn","usage":{"input_tokens":5,""" +
        """"output_tokens":7}}"""
    private val start = frame("""{"type":"message_start","message":{"model":"actual-model","content":[]}}""")
    private val terminal = frame("""{"type":"message_delta","delta":{"stop_reason":"end_turn"}}""") +
        frame("""{"type":"message_stop"}""")

    @Test
    fun ordinaryBrokerAndSessionRecoverWithoutStreamingSubstitution(): Unit = runBlocking {
        for (caller in listOf("gateway", "broker", "session")) {
            val events = mutableListOf<RecoveryEvent>()
            val wires = mutableListOf<RecoveryWire>()
            RecoveryScriptedServer(listOf(ScriptedReply(503, "private error"), ScriptedReply(200, success))).use { server ->
                val gateway = AnthropicGateway("key-private", server.url)
                try {
                    val config = CompletionConfig(temperature = 0.2, maxTokens = 127, recovery = policy(events, wires))
                    val result = when (caller) {
                        "broker" -> LlmBroker(gateway).complete("claude", messages, config = config)
                        "session" -> ChatSession(LlmBroker(gateway), "claude", "system-private", config = config).send("payload-private")
                        else -> gateway.complete("claude", messages, config = config)
                    }
                    assertEquals("ok", result.content)
                    assertEquals("reason", result.thinking)
                    assertEquals("actual-model", result.providerModel)
                    assertEquals("end_turn", result.finishReason)
                    assertEquals(5, result.usage?.get("input_tokens")?.toString()?.toInt())
                    assertEquals(2, server.requests.size)
                    assertContentEquals(server.requests[0], server.requests[1])
                    val payload = Json.parseToJsonElement(server.requests[0].decodeToString()) as JsonObject
                    assertEquals(null, payload["stream"], "ordinary calls must remain ordinary")
                    assertEquals("127", payload["max_tokens"].toString())
                    assertEquals("0.2", payload["temperature"].toString())
                    assertTrue(server.requestHeaders.all { head -> head.contains("x-api-key: key-private") })
                    assertTrue(server.requestHeaders.all { head -> head.contains("anthropic-version: 2023-06-01") })
                    assertTrue(server.requestHeaders.none { head -> head.any { it.startsWith("Authorization:") } })
                    val started = events.filter { it.stage == RecoveryStage.STARTED }
                    assertEquals(listOf(1, 2), started.map { it.identity.attemptNumber })
                    assertEquals(1, started.map { it.identity.logicalId }.distinct().size)
                    assertEquals(2, started.map { it.identity.attemptId }.distinct().size)
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
                    assertEquals(503, events.last().failures.single().status)
                    assertContentEquals(server.requests[0], wires.first().inspectRequest())
                    assertContentEquals(success.encodeToByteArray(), wires.last().inspectResponse())
                } finally {
                    gateway.close()
                }
            }
        }
    }

    @Test
    fun structuredRecoveryKeepsForcedToolSchemaAndDoesNotReplayTools(): Unit = runBlocking {
        val body = """{"content":[{"type":"tool_use","id":"tool-1","name":"respond_in_json","input":{"answer":"ok"}}]}"""
        RecoveryScriptedServer(listOf(ScriptedReply(503, ""), ScriptedReply(200, body))).use { server ->
            val gateway = AnthropicGateway("key", server.url)
            try {
                val schema = buildJsonObject { put("type", "object") }
                val result = gateway.completeJson("claude", messages, schema, CompletionConfig(recovery = policy()))
                assertEquals("\"ok\"", result["answer"].toString())
                assertContentEquals(server.requests[0], server.requests[1])
                val payload = server.requests[0].decodeToString()
                assertTrue(payload.contains("respond_in_json"))
                assertTrue(payload.contains("input_schema"))
                assertFalse(payload.contains("\"stream\":true"))
            } finally {
                gateway.close()
            }
        }
    }

    @Test
    fun defaultAndPermanentTruncatedErrorsNeverResend(): Unit = runBlocking {
        for (status in listOf(400, 401, 403, 504)) {
            val observed = CountDownLatch(1)
            RecoveryScriptedServer(listOf(ScriptedReply(status, "private", truncated = true, release = observed))).use { server ->
                val gateway = AnthropicGateway("key", server.url)
                try {
                    val failure = assertFailsWith<RecoveryException> {
                        gateway.complete(
                            "claude",
                            messages,
                            config = CompletionConfig(
                                recovery =
                                RecoveryPolicy(
                                    maxAttempts = if (status == 504) 1 else 3,
                                    admission = { true },
                                    capture = { if (it.inspectResponse()?.size == "private".length) observed.countDown() },
                                ),
                            ),
                        )
                    }
                    assertEquals(status, failure.failures.single().status)
                    assertEquals(1, server.requests.size)
                    if (status != 504) assertFalse(failure.failures.single().eligible)
                    assertIs<java.io.IOException>(failure.failures.single().inspectCause())
                    assertContentEquals("private".encodeToByteArray(), failure.failures.single().inspectBytes())
                    assertTrue(failure.failures.single().progress.headersReceived)
                } finally {
                    observed.countDown()
                    gateway.close()
                }
            }
        }
        RecoveryScriptedServer(listOf(ScriptedReply(503, "error"))).use { server ->
            val gateway = AnthropicGateway("key", server.url)
            try {
                assertFailsWith<LlmGatewayException> { gateway.complete("claude", messages) }
                assertEquals(1, server.requests.size)
            } finally {
                gateway.close()
            }
        }
    }

    @Test
    fun persistent504HasOrderedBoundedHistory(): Unit = runBlocking {
        RecoveryScriptedServer(List(3) { ScriptedReply(504, "private-$it") }).use { server ->
            val gateway = AnthropicGateway("key", server.url)
            try {
                val failure = assertFailsWith<RecoveryException> {
                    gateway.complete("claude", messages, config = CompletionConfig(recovery = policy()))
                }
                assertEquals(RecoveryReason.EXHAUSTED, failure.reason)
                assertEquals(listOf(1, 2), failure.failures.map { it.identity.attemptNumber })
                assertTrue(failure.failures.all { it.status == 504 })
                assertEquals(2, server.requests.size)
            } finally {
                gateway.close()
            }
        }
    }

    @Test
    fun retryAfterSecondsDatesAndInvalid(): Unit = runBlocking {
        for ((header, expected) in listOf(
            "2" to 2000L,
            "Thu, 01 Jan 1970 00:00:03 GMT" to 3000L,
            "Wed, 31 Dec 1969 23:59:59 GMT" to 5L,
            "invalid" to 5L,
        )) {
            val delays = mutableListOf<Long>()
            val events = mutableListOf<RecoveryEvent>()
            RecoveryScriptedServer(
                listOf(ScriptedReply(429, "", mapOf("Retry-After" to header)), ScriptedReply(200, success)),
            ).use { server ->
                val gateway = AnthropicGateway("key", server.url)
                try {
                    gateway.complete(
                        "claude",
                        messages,
                        config = CompletionConfig(
                            recovery = RecoveryPolicy(
                                maxAttempts = 2,
                                admission = { true },
                                baseDelayMillis = 5,
                                delayCeilingMillis = 5000,
                                clock = { 0 },
                                jitter = { 5 },
                                sleep = { delays += it },
                                observer = { events += it },
                            ),
                        ),
                    )
                    assertEquals(listOf(expected), delays)
                    val parsed = events.first { it.stage == RecoveryStage.FAILED }.failure?.retryAfter
                    if (header == "invalid") assertIs<RecoveryRetryAfter.Invalid>(parsed) else assertIs<RecoveryRetryAfter.Delay>(parsed)
                } finally {
                    gateway.close()
                }
            }
        }
    }

    @Test
    fun retryAfterMinimumRefusesCeilingOrBudget(): Unit = runBlocking {
        for (budget in listOf<Long?>(null, 100)) {
            RecoveryScriptedServer(listOf(ScriptedReply(429, "", mapOf("Retry-After" to "10")))).use { server ->
                val gateway = AnthropicGateway("key", server.url)
                try {
                    val failure = assertFailsWith<RecoveryException> {
                        gateway.complete(
                            "claude",
                            messages,
                            config = CompletionConfig(
                                recovery = RecoveryPolicy(
                                    maxAttempts = 2,
                                    admission = { true },
                                    delayCeilingMillis = if (budget == null) 1000 else 20000,
                                    budgetMillis = budget,
                                ),
                            ),
                        )
                    }
                    assertEquals(if (budget == null) RecoveryReason.PROVIDER_MINIMUM else RecoveryReason.BUDGET, failure.reason)
                    assertEquals(1, server.requests.size)
                } finally {
                    gateway.close()
                }
            }
        }
    }

    @Test
    fun keepaliveOnlyCanRecoverButContentReasoningAndToolFragmentsInterrupt(): Unit = runBlocking {
        val partials = listOf(
            frame("""{"type":"ping"}"""),
            frame("""{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""") +
                frame("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"private"}}"""),
            frame("""{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}""") +
                frame("""{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"private"}}"""),
            frame(
                """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"t",""" +
                    """"name":"count","input":{}}}""",
            ) +
                frame("""{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{"}}"""),
        )
        for ((index, partial) in partials.withIndex()) {
            val events = mutableListOf<RecoveryEvent>()
            RecoveryScriptedServer(listOf(ScriptedReply(200, start + partial), ScriptedReply(200, start + terminal))).use { server ->
                val gateway = AnthropicGateway("key", server.url)
                try {
                    val output = mutableListOf<GatewayStreamEvent>()
                    val config = CompletionConfig(recovery = policy(events))
                    if (index == 0) {
                        gateway.stream("claude", messages, config = config).collect { output += it }
                        assertEquals(2, server.requests.size)
                        val first = events.first { it.stage == RecoveryStage.FAILED }.failure
                        assertIs<RecoveryStreamClosedException>(first?.inspectCause())
                        assertTrue(requireNotNull(first).progress.rawBytes > 0)
                        assertFalse(first.progress.semanticObserved)
                    } else {
                        val failure = assertFailsWith<RecoveryException> {
                            gateway.stream("claude", messages, config = config).collect { output += it }
                        }
                        assertEquals(RecoveryReason.PARTIAL_SUCCESS, failure.reason)
                        assertEquals(1, server.requests.size)
                        assertTrue(failure.failures.single().progress.semanticObserved)
                        assertEquals(if (index == 3) 0 else 1, output.size)
                        assertEquals(RecoveryStage.INTERRUPTED, events.last().stage)
                    }
                } finally {
                    gateway.close()
                }
            }
        }
    }

    @Test
    fun malformedFramesAndMalformedStructuredRepliesStayIneligible(): Unit = runBlocking {
        for (body in listOf("data: not-json\n\n", frame("""{"type":"unknown"}"""), terminal.replace("end_turn", "unknown_reason"))) {
            RecoveryScriptedServer(listOf(ScriptedReply(200, start + body))).use { server ->
                val gateway = AnthropicGateway("key", server.url)
                try {
                    val failure = assertFailsWith<RecoveryException> {
                        gateway.stream("claude", messages, config = CompletionConfig(recovery = policy())).collect {}
                    }
                    assertFalse(failure.failures.single().eligible)
                    assertEquals(1, server.requests.size)
                } finally {
                    gateway.close()
                }
            }
        }
        RecoveryScriptedServer(listOf(ScriptedReply(200, "{}"))).use { server ->
            val gateway = AnthropicGateway("key", server.url)
            try {
                assertFailsWith<RecoveryException> { gateway.complete("claude", messages, config = CompletionConfig(recovery = policy())) }
                assertEquals(1, server.requests.size)
            } finally {
                gateway.close()
            }
        }
    }

    @Test
    fun admissionRejectsWithoutAnotherWireRequest(): Unit = runBlocking {
        RecoveryScriptedServer(listOf(ScriptedReply(503, ""))).use { server ->
            val gateway = AnthropicGateway("key", server.url)
            try {
                val failure = assertFailsWith<RecoveryException> {
                    gateway.complete(
                        "claude",
                        messages,
                        config = CompletionConfig(
                            recovery = RecoveryPolicy(maxAttempts = 2, admission = {
                                false
                            }),
                        ),
                    )
                }
                assertEquals(RecoveryReason.ADMISSION_REJECTED, failure.reason)
                assertEquals(1, server.requests.size)
            } finally {
                gateway.close()
            }
        }
    }

    @Test
    fun cancellationWinsInRequestAdmissionBackoffAndPausedToolDelivery(): Unit = runBlocking {
        for (phase in listOf("request", "admission", "backoff", "tools", "terminal")) {
            val reached = CompletableDeferred<Unit>()
            val caught = CompletableDeferred<RecoveryCancellationException>()
            val events = mutableListOf<RecoveryEvent>()
            val release = CountDownLatch(1)
            lateinit var job: kotlinx.coroutines.Job
            val body =
                frame(
                    """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"t",""" +
                        """"name":"count","input":{}}}""",
                ) +
                    frame("""{"type":"content_block_stop","index":0}""") + terminal
            val reply = when (phase) {
                "request" -> ScriptedReply(200, ":", truncated = true, release = release)
                "tools" -> ScriptedReply(200, start + body)
                "terminal" -> ScriptedReply(200, start + terminal)
                else -> ScriptedReply(503, "")
            }
            RecoveryScriptedServer(listOf(reply)).use { server ->
                val gateway = AnthropicGateway("key", server.url)
                try {
                    val policy = cancellationPolicy(phase, events, reached) { job.cancel() }
                    job = launch {
                        try {
                            gateway.stream("claude", messages, config = CompletionConfig(recovery = policy)).collect {
                                if (phase == "tools") {
                                    reached.complete(Unit)
                                    awaitCancellation()
                                }
                            }
                        } catch (failure: RecoveryCancellationException) {
                            caught.complete(failure)
                        }
                    }
                    withTimeout(5000) { reached.await() }
                    assertEquals(1, server.requests.size, "pending work is not admission")
                    job.cancel()
                    withTimeout(5000) { job.join() }
                    release.countDown()
                    val failure = withTimeout(5000) { caught.await() }
                    assertEquals(1, failure.wireAttempts)
                    assertEquals(RecoveryStage.CANCELLED, events.last().stage)
                    assertEquals(1, events.count { it.stage == RecoveryStage.CANCELLED })
                    assertTrue(events.any { it.stage == RecoveryStage.FAILED })
                    assertFalse(events.any { it.stage == RecoveryStage.SUCCEEDED })
                } finally {
                    release.countDown()
                    gateway.close()
                }
            }
        }
    }

    @Test
    fun brokerToolsExecuteOnceAcrossFollowupRecovery(): Unit = runBlocking {
        var executions = 0
        val tool = object : LlmTool {
            override val descriptor = ToolDescriptor("count", "count", buildJsonObject { put("type", "object") })
            override suspend fun execute(arguments: JsonObject): String {
                executions++
                return "result"
            }
        }
        val toolBody = """{"content":[{"type":"tool_use","id":"t","name":"count","input":{}}]}"""
        RecoveryScriptedServer(listOf(ScriptedReply(200, toolBody), ScriptedReply(503, ""), ScriptedReply(200, success))).use { server ->
            val gateway = AnthropicGateway("key", server.url)
            try {
                val response = ChatSession(
                    LlmBroker(gateway),
                    "claude",
                    tools = listOf(tool),
                    config = CompletionConfig(recovery = policy()),
                ).send("payload")
                assertEquals("ok", response.content)
                assertEquals(1, executions)
                assertEquals(3, server.requests.size)
                assertContentEquals(server.requests[1], server.requests[2])
                assertTrue(server.requests[1].decodeToString().contains("tool_result"))
                assertTrue(server.requests.none { it.decodeToString().contains("\"stream\":true") })
            } finally {
                gateway.close()
            }
        }
    }

    @Test
    fun streamingBrokerAndSessionCompleteToolsOnce(): Unit = runBlocking {
        for (sessionCaller in listOf(false, true)) {
            var executions = 0
            val tool = object : LlmTool {
                override val descriptor = ToolDescriptor("count", "count", buildJsonObject { put("type", "object") })
                override suspend fun execute(arguments: JsonObject): String {
                    executions++
                    return "result"
                }
            }
            val tools =
                start +
                    frame(
                        """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"t",""" +
                            """"name":"count","input":{}}}""",
                    ) +
                    frame("""{"type":"content_block_stop","index":0}""") + terminal.replace("end_turn", "tool_use")
            RecoveryScriptedServer(
                listOf(ScriptedReply(200, tools), ScriptedReply(503, ""), ScriptedReply(200, start + terminal)),
            ).use { server ->
                val gateway = AnthropicGateway("key", server.url)
                try {
                    val config = CompletionConfig(recovery = policy())
                    val broker = LlmBroker(gateway)
                    if (sessionCaller) {
                        ChatSession(broker, "claude", tools = listOf(tool), config = config).stream("payload").collect {}
                    } else {
                        broker.stream("claude", messages, listOf(tool), config).collect {}
                    }
                    assertEquals(1, executions)
                    assertEquals(3, server.requests.size)
                    assertContentEquals(server.requests[1], server.requests[2])
                    assertTrue(server.requests.all { it.decodeToString().contains("\"stream\":true") })
                } finally {
                    gateway.close()
                }
            }
        }
    }

    @Test
    fun safeSummariesWithholdCredentialPayloadAndHeaderEchoes(): Unit = runBlocking {
        val key = "req_012345678901234567890123"
        val body = """{"error":{"type":"overloaded_error","message":"$key payload-private"}}"""
        RecoveryScriptedServer(listOf(ScriptedReply(503, body, mapOf("request-id" to key, "x-api-key" to key)))).use { server ->
            val gateway = AnthropicGateway(key, server.url)
            val events = mutableListOf<RecoveryEvent>()
            try {
                val failure = assertFailsWith<RecoveryException> {
                    gateway.complete(
                        "claude",
                        listOf(LlmMessage.user("overloaded_error payload-private")),
                        config = CompletionConfig(recovery = RecoveryPolicy(observer = { events += it })),
                    )
                }
                val attempt = failure.failures.single()
                assertEquals(null, attempt.providerRequestId)
                assertEquals(null, attempt.providerCode)
                val safe = failure.toString() + attempt.summary().toString() + events.joinToString()
                assertFalse(safe.contains(key))
                assertFalse(safe.contains("payload-private"))
                assertFalse(safe.contains("overloaded_error"))
                assertContentEquals(body.encodeToByteArray(), attempt.inspectBytes())
                assertEquals(listOf(key), attempt.inspectHeaders()["x-api-key"])
            } finally {
                gateway.close()
            }
        }
    }

    private fun cancellationPolicy(
        phase: String,
        events: MutableList<RecoveryEvent>,
        reached: CompletableDeferred<Unit>,
        cancel: () -> Unit,
    ): RecoveryPolicy = RecoveryPolicy(
        maxAttempts = 2,
        observer = { events += it },
        capture = {
            if (phase == "request" && it.status != null) reached.complete(Unit)
            if (phase == "terminal" && it.complete) {
                reached.complete(Unit)
                cancel()
            }
        },
        admission = {
            if (phase == "admission") {
                reached.complete(Unit)
                awaitCancellation()
            }
            true
        },
        sleep = {
            if (phase == "backoff") {
                reached.complete(Unit)
                awaitCancellation()
            }
        },
    )

    private fun policy(
        events: MutableList<RecoveryEvent> = mutableListOf(),
        wires: MutableList<RecoveryWire> = mutableListOf(),
    ): RecoveryPolicy =
        RecoveryPolicy(
            maxAttempts = 2,
            admission = { true },
            baseDelayMillis = 0,
            jitter = { 0 },
            sleep = {},
            observer = { events += it },
            capture = { wires += it },
        )

    private fun frame(payload: String): String = "data: $payload\n\n"
}
