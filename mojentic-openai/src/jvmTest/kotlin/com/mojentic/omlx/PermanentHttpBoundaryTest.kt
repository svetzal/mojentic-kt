package com.mojentic.omlx

import com.mojentic.llm.ChatSession
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmGateway
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.StreamErrorReason
import com.mojentic.llm.StreamEventsGateway
import com.mojentic.llm.recovery.RecoveryCategory
import com.mojentic.llm.recovery.RecoveryEvent
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryFailure
import com.mojentic.llm.recovery.RecoveryOperation
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryReason
import com.mojentic.llm.recovery.RecoverySemanticProgress
import com.mojentic.llm.recovery.RecoveryStage
import com.mojentic.llm.recovery.RecoveryWire
import com.mojentic.llm.recovery.recoveryHttpClient
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import com.mojentic.omlx.RecoveryTestFixtures.Provider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Permanent statuses take precedence over a truncated body through public callers. */
class PermanentHttpBoundaryTest {
    @Serializable
    private data class Answer(val value: String)

    private val messages = listOf(LlmMessage.user("payload"))
    private val schema = Json.parseToJsonElement("""{"type":"object"}""") as JsonObject
    private val bytes = "private-é⚙".encodeToByteArray()

    private enum class Caller(val operation: String) {
        COMPLETE("complete"),
        STRUCTURED("structured"),
        STRUCTURED_RESPONSE("structured"),
        STREAM("stream"),
        EVENTS("streamEvents"),
        BROKER_EVENTS("streamEvents"),
        BROKER_COMPLETE("complete"),
        BROKER_GENERATE("complete"),
        BROKER_STRUCTURED("structured"),
        BROKER_STREAM("stream"),
        SESSION_SEND("complete"),
        SESSION_STREAM("stream"),
    }

    private suspend fun invoke(
        gateway: LlmGateway,
        caller: Caller,
        config: CompletionConfig,
        tools: List<LlmTool>,
        delivered: MutableList<Any>,
    ) {
        val broker = LlmBroker(gateway)
        val session = ChatSession(broker, "claude", tools = tools, config = config)
        try {
            when (caller) {
                Caller.COMPLETE -> delivered += gateway.complete("claude", messages, tools, config)

                Caller.STRUCTURED -> delivered += gateway.completeJson("claude", messages, schema, config)

                Caller.STRUCTURED_RESPONSE -> delivered += gateway.completeJsonResponse("claude", messages, schema, config)

                Caller.STREAM -> gateway.stream("claude", messages, tools, config).collect { delivered += it }

                Caller.EVENTS -> {
                    val output = mutableListOf<CompletionStreamEvent>()
                    (gateway as StreamEventsGateway).streamEvents("claude", messages, config).collect { output += it }
                    val error = assertIs<CompletionStreamEvent.Error>(output.single())
                    throw (error.reason as StreamErrorReason.RequestFailed).cause
                }

                Caller.BROKER_EVENTS -> {
                    val output = mutableListOf<CompletionStreamEvent>()
                    broker.generateStreamEvents("claude", messages, config).collect { output += it }
                    val error = assertIs<CompletionStreamEvent.Error>(output.single())
                    throw (error.reason as StreamErrorReason.RequestFailed).cause
                }

                Caller.BROKER_COMPLETE -> delivered += broker.complete("claude", messages, tools, config)

                Caller.BROKER_GENERATE -> delivered += broker.generateResponse("claude", messages, tools, config)

                Caller.BROKER_STRUCTURED -> delivered += broker.completeJson<Answer>("claude", messages, config)

                Caller.BROKER_STREAM -> broker.stream("claude", messages, tools, config).collect { delivered += it }

                Caller.SESSION_SEND -> delivered += session.send("payload")

                Caller.SESSION_STREAM -> session.stream("payload").collect { delivered += it }
            }
        } finally {
            assertTrue(session.messages().isEmpty(), "failed session must roll back history")
        }
    }

    private suspend fun check(
        gateway: LlmGateway,
        server: RecoveryScriptedServer,
        caller: Caller,
        status: Int,
        release: CountDownLatch,
        expected: String,
        engine: TruncatedResponseEngine,
    ) {
        val events = mutableListOf<RecoveryEvent>()
        val wires = mutableListOf<RecoveryWire>()
        val delivered = mutableListOf<Any>()
        var admissions = 0
        var executions = 0
        val tool = object : LlmTool {
            override val descriptor = ToolDescriptor("proof", "proof tool", schema)
            override suspend fun execute(arguments: JsonObject): String {
                executions++
                return "unexpected tool execution"
            }
        }
        val policy = RecoveryPolicy(
            maxAttempts = 3,
            retryableStatuses = setOf(400, 401, 403),
            retryTransport = true,
            admission = {
                admissions++
                true
            },
            sleep = {},
            observer = { events += it },
            capture = {
                wires += it
                if (it.inspectResponse()?.size == bytes.size && !it.complete) {
                    release.countDown()
                    engine.awaitFailure()
                }
            },
        )
        val failure = assertFailsWith<RecoveryException> {
            invoke(
                gateway,
                caller,
                CompletionConfig(temperature = 0.2, numCtx = 512, maxTokens = 127, recovery = policy),
                listOf(tool),
                delivered,
            )
        }
        assertTrue(delivered.isEmpty())
        assertEquals(0, admissions)
        assertEquals(0, executions)
        assertEquals(null, failure.cause)
        val safe = failure.toString() + failure.summary() + events + wires
        listOf("private", "payload", "proof tool").forEach { assertFalse(safe.contains(it)) }
        assertEquals(1, failure.wireAttempts)
        assertEquals(RecoveryReason.HTTP_PERMANENT, failure.reason)
        val attempt = failure.failures.single()
        assertEquals(Json.decodeFromString<RecoveryOperation>(Json.encodeToString(caller.operation)), attempt.operation)
        assertFailure(attempt, status)
        assertLifecycle(attempt, events, wires, expected)
        assertContentEquals(expected.encodeToByteArray(), server.requests.single())
    }

    private fun assertFailure(attempt: RecoveryFailure, status: Int) {
        assertEquals(RecoveryCategory.HTTP, attempt.category)
        assertEquals(status, attempt.status)
        assertFalse(attempt.eligible)
        assertEquals(RecoveryReason.HTTP_PERMANENT, attempt.reason)
        assertTrue(attempt.wireSent)
        assertEquals(1, attempt.identity.attemptNumber)
        assertContentEquals(bytes, attempt.inspectBytes())
        assertEquals(bytes.size.toLong(), attempt.progress.rawBytes)
        assertTrue(attempt.progress.headersReceived)
        assertFalse(attempt.progress.semanticObserved)
        assertFalse(attempt.progress.semanticDelivered)
        assertFalse(attempt.progress.replayUnsafe)
        assertEquals(RecoverySemanticProgress(), attempt.progress.observed)
        assertEquals(RecoverySemanticProgress(), attempt.progress.delivered)
        assertIs<java.io.IOException>(attempt.inspectCause())
        assertSame(attempt.inspectCause(), attempt.inspectBoundaryCause())
        assertEquals(
            mapOf(
                "content-type" to listOf("application/json"),
                "content-length" to listOf((bytes.size + 20).toString()),
                "connection" to listOf("close"),
                "x-proof" to listOf("permanent"),
            ),
            attempt.inspectHeaders(),
        )
    }

    private fun assertLifecycle(attempt: RecoveryFailure, events: List<RecoveryEvent>, wires: List<RecoveryWire>, expected: String) {
        assertEquals(listOf(RecoveryStage.STARTED, RecoveryStage.FAILED, RecoveryStage.EXHAUSTED), events.map { it.stage })
        assertTrue(events.all { it.identity == attempt.identity })
        assertTrue(wires.all { it.identity == attempt.identity })
        assertEquals(listOf(0, 1, 1), events.map { it.wireAttempts })
        events.filter { it.failure != null }.forEach {
            assertSame(attempt, it.failure)
            assertSame(attempt.inspectCause(), it.failure?.inspectCause())
            assertSame(attempt, it.failures.single())
        }
        assertEquals(1, wires.count { it.inspectResponse() == null })
        assertTrue(wires.last().complete)
        wires.forEach {
            assertContentEquals(expected.encodeToByteArray(), it.inspectRequest())
            if (it.inspectResponse() != null) {
                assertEquals(attempt.status, it.status)
                assertEquals(attempt.inspectHeaders(), it.inspectHeaders())
            }
        }
        assertContentEquals(bytes, wires.last().inspectResponse())
        assertEquals(bytes.size.toLong(), wires.last().progress.rawBytes)
    }

    private fun schemaText(caller: Caller): String = if (caller == Caller.BROKER_STRUCTURED) {
        """{"type":"object","properties":{"value":{"type":"string"}},"required":["value"]}"""
    } else {
        """{"type":"object"}"""
    }

    @Test
    fun truncatedPermanentAllPublicBoundaries(): Unit = runBlocking {
        for (provider in Provider.entries) {
            for (status in listOf(400, 401, 403)) {
                for (caller in Caller.entries) {
                    val release = CountDownLatch(1)
                    RecoveryScriptedServer(
                        listOf(
                            ScriptedReply(
                                status,
                                bytes.decodeToString(),
                                mapOf("X-Proof" to "permanent"),
                                truncated = true,
                                release = release,
                            ),
                            ScriptedReply(200, "distinguishable-unauthorized-resend"),
                        ),
                    ).use { server ->
                        val client = recoveryHttpClient()
                        val engine = TruncatedResponseEngine(client.engine)
                        provider.gateway(server.url, engine = engine).use { gateway ->
                            try {
                                check(gateway.value, server, caller, status, release, expected(provider, caller), engine)
                                val path = when (provider) {
                                    Provider.OLLAMA -> "/api/chat"
                                    Provider.OMLX -> "/v1/chat/completions"
                                    Provider.OPENAI -> "/chat/completions"
                                }
                                assertEquals(listOf("POST $path HTTP/1.1"), server.requestLines)
                            } finally {
                                release.countDown()
                                client.close()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun expected(provider: Provider, caller: Caller): String {
        val streaming = caller.operation in setOf("stream", "streamEvents")
        val structured = caller.operation == "structured"
        val schemaName = if (provider == Provider.OPENAI) "structured_response" else "response"
        val strict = if (provider == Provider.OPENAI) ""","strict":false""" else ""
        val message = if (provider == Provider.OLLAMA) {
            """"messages":[{"role":"user","content":"payload"}]"""
        } else {
            """"messages":[{"role":"user","content":[{"type":"text","text":"payload"}]}]"""
        }
        val tool = """{"function":{"name":"proof","description":"proof tool","parameters":{"type":"object"}}}"""
        return if (provider == Provider.OLLAMA) {
            """{"model":"claude",$message""" + (if (streaming) ""","stream":true""" else "") +
                ""","options":{"temperature":0.2,"num_ctx":512,"num_predict":127}""" +
                (
                    if (structured) {
                        ""","format":${schemaText(caller)}}"""
                    } else if (caller in
                        setOf(Caller.EVENTS, Caller.BROKER_EVENTS)
                    ) {
                        "}"
                    } else {
                        ""","tools":[$tool]}"""
                    }
                    )
        } else {
            """{"model":"claude",$message,"temperature":0.2,"max_tokens":127""" +
                (if (streaming) ""","stream":true""" else "") +
                (
                    if (structured) {
                        ""","response_format":{"type":"json_schema","json_schema":{"name":"$schemaName",""" +
                            """"schema":${schemaText(caller)}$strict}}"""
                    } else if (caller in setOf(Caller.EVENTS, Caller.BROKER_EVENTS)) {
                        ""
                    } else {
                        ""","tools":[${tool.replace("{\"function\":", "{\"type\":\"function\",\"function\":")}]"""
                    }
                    ) +
                (if (streaming) ""","stream_options":{"include_usage":true}}""" else "}")
        }
    }
}
