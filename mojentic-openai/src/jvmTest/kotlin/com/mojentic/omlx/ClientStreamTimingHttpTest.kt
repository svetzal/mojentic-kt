package com.mojentic.omlx

import com.mojentic.anthropic.AnthropicGateway
import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.CompletionEvidence
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmGateway
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.StreamErrorReason
import com.mojentic.llm.StreamEvent
import com.mojentic.llm.StreamEventsGateway
import com.mojentic.llm.recovery.RecoveryException
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryStage
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import com.mojentic.ollama.OllamaGateway
import com.mojentic.openai.OpenAIGateway
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit

/**
 * Consumer contracts derived from MODEL-METADATA section 2 and the read-only
 * ts-stream-first-token-v1 behavioral reference. Every response crosses an injected
 * in-process HTTP transport and the public provider adapter.
 */
class ClientStreamTimingHttpTest {
    private class Clock : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
        val now = AtomicLong(0)
        override fun read(): Long = now.get()
    }

    private enum class Provider {
        OPENAI,
        OMLX,
        OLLAMA,
        ANTHROPIC,
        ;

        fun gateway(host: String, engine: io.ktor.client.engine.HttpClientEngine): LlmGateway = when (this) {
            OPENAI -> OpenAIGateway("test", host = "$host/v1", engine = engine)
            OMLX -> OmlxGateway(host = host, apiKey = "test", engine = engine)
            OLLAMA -> OllamaGateway(host = host, engine = engine)
            ANTHROPIC -> AnthropicGateway("test", host = host, engine = engine)
        }

        fun prefix(): String = when (this) {
            OMLX -> "data: {\"model\":\"keepalive\",\"choices\":[{\"delta\":{\"content\":\"ignore\"}}]}\n\n"

            OLLAMA -> "\n{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"thinking\":\"\"},\"done\":false}\n"

            ANTHROPIC ->
                ": ping\n\ndata: {\"type\":\"ping\"}\n\n" +
                    "data: {\"type\":\"message_start\",\"message\":{\"model\":\"test\",\"content\":[]}}\n\n"

            OPENAI -> ": ping\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"\",\"reasoning_content\":\"\"}}]}\n\n"
        }

        fun token(kind: String): String = when (this) {
            OPENAI, OMLX -> {
                val delta = when (kind) {
                    "plain_content" -> "\"content\":\"visible\""
                    "thinking" -> "\"content\":\"visible\",\"reasoning_content\":\"private\""
                    "content" -> "\"content\":\"visible\",\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\"}}]"
                    else -> "\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\"}}]"
                }
                "data: {\"choices\":[{\"delta\":{$delta}}]}\n\n"
            }

            OLLAMA -> when (kind) {
                "thinking" -> "{\"message\":{\"role\":\"assistant\",\"content\":\"visible\",\"thinking\":\"private\"},\"done\":false}\n"

                "content", "plain_content" -> "{\"message\":{\"role\":\"assistant\",\"content\":\"visible\"},\"done\":false}\n"

                else ->
                    "{\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"function\":{\"name\":\"lookup" +
                        "\",\"arguments\":{}}}]},\"done\":false}\n"
            }

            ANTHROPIC -> anthropicBlock(kind) + when (kind) {
                "thinking" ->
                    "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"thinking_de" +
                        "lta\",\"thinking\":\"private\",\"text\":\"visible\"}}\n\n"

                "content", "plain_content" ->
                    "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta" +
                        "\",\"text\":\"visible\"}}\n\n"

                else ->
                    "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_" +
                        "delta\",\"partial_json\":\"{\"}}\n\n"
            }
        }

        private fun anthropicBlock(kind: String): String {
            val block = when (kind) {
                "thinking" -> "\"type\":\"thinking\",\"thinking\":\"\""
                "content", "plain_content" -> "\"type\":\"text\",\"text\":\"\""
                else -> "\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"lookup\",\"input\":{}"
            }
            return "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{$block}}\n\n"
        }

        fun terminal(hasToken: Boolean = true): String = when (this) {
            OPENAI, OMLX ->
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_to" +
                    "kens\":2,\"time_to_first_token\":0.7,\"prompt_eval_duration\":0.2,\"generation_durati" +
                    "on\":0.5,\"total_time\":0.9}}\n\ndata: [DONE]\n\n"

            OLLAMA ->
                "{\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"done_reason\":" +
                    "\"stop\",\"total_duration\":900,\"prompt_eval_duration\":200}\n"

            ANTHROPIC -> (if (hasToken) "data: {\"type\":\"content_block_stop\",\"index\":0}\n\n" else "") +
                "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":" +
                "{\"output_tokens\":3}}\n\ndata: {\"type\":\"message_stop\"}\n\n"
        }
    }

    @Test
    fun publicStreamsMeasureExactKindsAndKeepProviderUnitsOnOrdinaryAndRecoveryPaths(): Unit = runTest {
        for (provider in Provider.entries) {
            for (recovery in listOf(false, true)) {
                println("timing case: $provider recovery=$recovery")
                // Mixed content+tool fragments are deliberately failures for the no-tools events API;
                // the legacy stream still exposes their terminal timing without waiting for completed tools.
                val clock = Clock()
                val reply = ScriptedReply(
                    200,
                    provider.prefix() + provider.token("thinking") + provider.terminal(),
                    beforeReply = { clock.now.addAndGet(25) },
                )
                RecoveryScriptedTransport(listOf(reply, reply)).use { server ->
                    val gateway = provider.gateway(server.url, engine = server.engine)
                    try {
                        val config = config(clock, recovery)
                        val stream = gateway.stream("test", listOf(LlmMessage.user("hello")), config = config)
                        clock.now.set(1000) // Construction does not start a request or the completion clock.
                        assertEquals(0, server.requests.size)
                        val events = stream.toList()
                        val terminal = assertIs<GatewayStreamEvent.Completed>(events.last())
                        assertTiming(terminal.evidence, "thinking", 25, 25)
                        assertEquals(1, server.requests.size)
                        if (provider == Provider.OMLX) {
                            assertEquals(JsonPrimitive(0.7), terminal.evidence.usage?.get("time_to_first_token"))
                            assertEquals(JsonPrimitive(0.2), terminal.evidence.usage?.get("prompt_eval_duration"))
                            assertEquals(JsonPrimitive(0.5), terminal.evidence.usage?.get("generation_duration"))
                            assertEquals(JsonPrimitive(0.9), terminal.evidence.usage?.get("total_time"))
                        }
                        if (provider == Provider.OLLAMA) {
                            assertEquals(JsonPrimitive(900), terminal.evidence.metadata?.get("total_duration"))
                        }
                        if (gateway is StreamEventsGateway) {
                            val result = gateway.streamEvents("test", listOf(LlmMessage.user("hello")), config).toList().last()
                            assertTiming(assertIs<CompletionStreamEvent.Completed>(result).evidence, "thinking", 25, 25)
                            assertEquals(2, server.requests.size)
                        }
                    } finally {
                        close(gateway)
                    }
                }
            }
        }
    }

    @Test
    fun noTokenSuccessIgnoresKeepaliveAndEmptyFramesOnBothPublicApis(): Unit = runTest {
        for (provider in Provider.entries) {
            for (recovery in listOf(false, true)) {
                println("timing case: $provider recovery=$recovery")
                val clock = Clock()
                val reply = ScriptedReply(200, provider.prefix() + provider.terminal(false), beforeReply = { clock.now.addAndGet(17) })
                RecoveryScriptedTransport(listOf(reply, reply)).use { server ->
                    val gateway = provider.gateway(server.url, engine = server.engine)
                    try {
                        val legacy = gateway.stream("test", listOf(LlmMessage.user("hello")), config = config(clock, recovery)).toList()
                        assertTiming(assertIs<GatewayStreamEvent.Completed>(legacy.last()).evidence, null, null, 17)
                        if (gateway is StreamEventsGateway) {
                            val terminal = gateway.streamEvents("test", listOf(LlmMessage.user("hello")), config(clock, recovery))
                                .toList().last()
                            assertTiming(assertIs<CompletionStreamEvent.Completed>(terminal).evidence, null, null, 17)
                        }
                    } finally {
                        close(gateway)
                    }
                }
            }
        }
    }

    @Test
    fun failuresBeforeTokensAndArgumentFragmentsRetainExactTerminalEvidence() {
        for (provider in Provider.entries) {
            for (recovery in listOf(false, true)) {
                for (kind in listOf<String?>(null, "tool_call", "thinking", "content")) {
                    verifyFailure(provider, recovery, kind)
                }
            }
        }
    }

    private fun verifyFailure(provider: Provider, recovery: Boolean, kind: String?): Unit = runTest {
        val clock = Clock()
        val body = provider.prefix() + kind?.let { provider.token(it) }.orEmpty()
        val reply = ScriptedReply(
            if (kind == null) 400 else 200,
            body,
            beforeReply = { clock.now.addAndGet(31) },
        )
        val server = RecoveryScriptedTransport(listOf(reply))
        val gateway = provider.gateway(server.url, engine = server.engine)
        try {
            if (gateway is StreamEventsGateway) {
                val flow = gateway.streamEvents("test", listOf(LlmMessage.user("hello")), config(clock, recovery))
                val error = assertIs<CompletionStreamEvent.Error>(flow.toList().last())
                assertTiming(error.evidence, kind, kind?.let { 31 }, 31)
                if (recovery) {
                    val cause = assertIs<StreamErrorReason.RequestFailed>(error.reason).cause
                    val failure = assertIs<RecoveryException>(cause)
                    assertTiming(failure.failures.last().inspectCompletionEvidence(), kind, kind?.let { 31 }, 31)
                }
            } else if (recovery) {
                val failure = assertFailsWith<RecoveryException> {
                    gateway.stream("test", listOf(LlmMessage.user("hello")), config = config(clock, true)).toList()
                }
                assertTiming(failure.failures.last().inspectCompletionEvidence(), kind, kind?.let { 31 }, 31)
            } else if (kind == null) {
                val failure = assertFailsWith<LlmGatewayException> {
                    gateway.stream("test", listOf(LlmMessage.user("hello")), config = config(clock, false)).toList()
                }
                assertTiming(failure.completionEvidence, null, null, 31)
            } else {
                // Preserve the existing permissive legacy Anthropic EOF behavior.
                val terminal = gateway.stream("test", listOf(LlmMessage.user("hello")), config = config(clock, false))
                    .toList().last()
                assertTiming(assertIs<GatewayStreamEvent.Completed>(terminal).evidence, kind, 31, 31)
            }
        } finally {
            close(gateway)
            server.close()
        }
    }

    @Test
    fun retryIncludesAdmittedDelayAndNewCollectionStartsFreshClock(): Unit = runTest {
        for (provider in Provider.entries) {
            val clock = Clock()
            val fail = ScriptedReply(503, "busy", beforeReply = { clock.now.addAndGet(10) })
            val success = ScriptedReply(
                200,
                provider.prefix() + provider.token("thinking") + provider.terminal(),
                beforeReply = { clock.now.addAndGet(20) },
            )
            RecoveryScriptedTransport(listOf(fail, success, success)).use { server ->
                val gateway = provider.gateway(server.url, engine = server.engine)
                try {
                    val policy = RecoveryPolicy(
                        maxAttempts = 2,
                        admission = {
                            clock.now.addAndGet(7)
                            true
                        },
                        monotonicClock = { clock.now.get() },
                        jitter = { 0 },
                        sleep = { clock.now.addAndGet(40) },
                    )
                    val config = CompletionConfig(recovery = policy, streamTimeSource = clock)
                    val first = gateway.stream("test", listOf(LlmMessage.user("hello")), config = config).toList().last()
                    assertTiming(assertIs<GatewayStreamEvent.Completed>(first).evidence, "thinking", 77, 77)
                    clock.now.addAndGet(900) // Caller/tool work between logical completions is excluded.
                    val next = gateway.stream("test", listOf(LlmMessage.user("hello")), config = config).toList().last()
                    assertTiming(assertIs<GatewayStreamEvent.Completed>(next).evidence, "thinking", 20, 20)
                    assertEquals(3, server.requests.size)
                    assertEquals(server.requests[0].decodeToString(), server.requests[1].decodeToString())
                } finally {
                    close(gateway)
                }
            }
        }
    }

    @Test
    fun brokerToolContinuationStartsIndependentClockAfterToolExecution(): Unit = runTest {
        for (provider in Provider.entries) {
            for (recovery in listOf(false, true)) {
                println("timing case: $provider recovery=$recovery")
                val clock = Clock()
                val toolBody = when (provider) {
                    Provider.OPENAI, Provider.OMLX ->
                        "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"t1\",\"function" +
                            "\":{\"name\":\"lookup\",\"arguments\":\"{\"}}]}}]}\n\n" +
                            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"argument" +
                            "s\":\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}\n\ndata: [DONE]\n\n"

                    Provider.OLLAMA -> provider.token("tool_call") + provider.terminal()

                    Provider.ANTHROPIC -> provider.prefix() + provider.token("tool_call") +
                        "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_" +
                        "delta\",\"partial_json\":\"}\"}}\n\n" +
                        provider.terminal()
                }
                val first = ScriptedReply(200, toolBody, beforeReply = { clock.now.addAndGet(23) })
                val next = ScriptedReply(
                    200,
                    provider.prefix() + provider.token("plain_content") + provider.terminal(),
                    beforeReply = { clock.now.addAndGet(11) },
                )
                RecoveryScriptedTransport(listOf(first, next)).use { server ->
                    val gateway = provider.gateway(server.url, engine = server.engine)
                    var executions = 0
                    val tool = object : LlmTool {
                        override val descriptor = ToolDescriptor("lookup", "look up", buildJsonObject { put("type", "object") })
                        override suspend fun execute(arguments: JsonObject): String {
                            executions++
                            clock.now.addAndGet(500)
                            return "found"
                        }
                    }
                    try {
                        val events = LlmBroker(gateway).stream(
                            "test",
                            listOf(LlmMessage.user("hello")),
                            listOf(tool),
                            config = config(clock, recovery),
                        ).toList()
                        val evidence = events.filterIsInstance<StreamEvent.Completed>()
                        assertEquals(2, evidence.size, "$provider recovery=$recovery")
                        assertTiming(evidence[0].evidence, "tool_call", 23, 23)
                        assertTiming(evidence[1].evidence, "content", 11, 11)
                        assertEquals(1, executions)
                        assertEquals(2, server.requests.size)
                    } finally {
                        close(gateway)
                    }
                }
            }
        }
    }

    @Test
    fun timingConfigurationPreservesRawRequestBytesAndDefaults(): Unit = runTest {
        for (provider in Provider.entries) {
            for (recovery in listOf(false, true)) {
                val clock = Clock()
                val reply = ScriptedReply(200, provider.prefix() + provider.terminal(false))
                RecoveryScriptedTransport(listOf(reply, reply)).use { server ->
                    val gateway = provider.gateway(server.url, engine = server.engine)
                    try {
                        val measured = config(clock, recovery)
                        gateway.stream("test", listOf(LlmMessage.user("hello")), config = measured).toList()
                        gateway.stream(
                            "test",
                            listOf(LlmMessage.user("hello")),
                            config = measured.copy(streamTimeSource = Clock()),
                        ).toList()
                        kotlin.test.assertContentEquals(server.requests[0], server.requests[1])
                        assertEquals(server.requestHeaders[0], server.requestHeaders[1])
                    } finally {
                        close(gateway)
                    }
                }
            }
        }
    }

    @Test
    fun recoveryCaptureFailureRetainsArgumentTimingBeforeParserOrDelivery(): Unit = runTest {
        for (provider in Provider.entries) {
            val clock = Clock()
            val reply = ScriptedReply(
                200,
                provider.prefix() + provider.token("tool_call"),
                beforeReply = { clock.now.addAndGet(31) },
            )
            RecoveryScriptedTransport(listOf(reply, reply)).use { server ->
                val gateway = provider.gateway(server.url, engine = server.engine)
                var admissions = 0
                val delivered = mutableListOf<GatewayStreamEvent>()
                try {
                    val policy = RecoveryPolicy(
                        maxAttempts = 2,
                        monotonicClock = { clock.now.get() },
                        admission = {
                            admissions++
                            true
                        },
                        capture = { wire ->
                            check(wire.inspectResponse()?.size != reply.body.encodeToByteArray().size) { "capture interrupted" }
                        },
                    )
                    val failure = assertFailsWith<RecoveryException> {
                        gateway.stream(
                            "test",
                            listOf(LlmMessage.user("hello")),
                            config = CompletionConfig(recovery = policy),
                        ).collect { delivered += it }
                    }
                    assertTiming(failure.failures.last().inspectCompletionEvidence(), "tool_call", 31, 31)
                    assertEquals(0, admissions)
                    assertEquals(0, delivered.size)
                    assertEquals(1, server.requests.size)
                } finally {
                    close(gateway)
                }
            }
        }
    }

    @Test
    fun terminalFailureDurationIncludesPendingAdmissionWithoutRestartOrReplay(): Unit = runTest {
        val clock = Clock()
        val reply = ScriptedReply(503, "busy", beforeReply = { clock.now.addAndGet(10) })
        RecoveryScriptedTransport(listOf(reply, reply)).use { server ->
            val gateway = OmlxGateway(host = server.url, engine = server.engine)
            try {
                var observed: CompletionEvidence? = null
                val policy = RecoveryPolicy(
                    maxAttempts = 2,
                    monotonicClock = { clock.now.get() },
                    admission = {
                        clock.now.addAndGet(7)
                        false
                    },
                    observer = { event ->
                        if (event.stage == RecoveryStage.EXHAUSTED) observed = event.failure?.inspectCompletionEvidence()
                    },
                )
                val error = assertIs<CompletionStreamEvent.Error>(
                    gateway.streamEvents(
                        "test",
                        listOf(LlmMessage.user("hello")),
                        CompletionConfig(recovery = policy),
                    ).toList().last(),
                )
                assertTiming(error.evidence, null, null, 17)
                assertTiming(observed, null, null, 17)
                val failure = assertIs<RecoveryException>(assertIs<StreamErrorReason.RequestFailed>(error.reason).cause)
                assertTiming(failure.failures.last().inspectCompletionEvidence(), null, null, 17)
                assertEquals(1, server.requests.size)
            } finally {
                gateway.close()
            }
        }
    }

    private fun config(clock: Clock, recovery: Boolean): CompletionConfig = CompletionConfig(
        streamTimeSource = clock,
        recovery = if (recovery) RecoveryPolicy(monotonicClock = { clock.now.get() }) else null,
    )

    private fun assertTiming(evidence: CompletionEvidence?, kind: String?, first: Int?, duration: Int) {
        val metadata = requireNotNull(evidence).metadata
        assertEquals(JsonPrimitive(duration), metadata?.get("request_duration_ms"))
        if (kind == null) {
            assertNull(metadata?.get("time_to_first_token_ms"))
            assertNull(metadata?.get("first_token_kind"))
        } else {
            assertEquals(JsonPrimitive(first), metadata?.get("time_to_first_token_ms"))
            assertEquals(JsonPrimitive(kind), metadata?.get("first_token_kind"))
        }
    }

    private fun close(gateway: LlmGateway) {
        when (gateway) {
            is OpenAIGateway -> gateway.close()
            is OmlxGateway -> gateway.close()
            is OllamaGateway -> gateway.close()
            is AnthropicGateway -> gateway.close()
        }
    }
}
