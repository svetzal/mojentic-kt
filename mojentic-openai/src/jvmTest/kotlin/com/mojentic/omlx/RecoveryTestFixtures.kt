package com.mojentic.omlx

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmGateway
import com.mojentic.llm.LlmGatewayResponse
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.ReasoningEffort
import com.mojentic.llm.recovery.RecoveryEvent
import com.mojentic.llm.recovery.RecoveryFailure
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryStage
import com.mojentic.llm.recovery.RecoveryWire
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import com.mojentic.ollama.OllamaGateway
import com.mojentic.openai.OpenAIGateway
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Duration

/** Shared loopback inputs and exact wire assertions; no production transport substitution. */
internal object RecoveryTestFixtures {
    suspend fun matrix(block: suspend (Provider, Boolean) -> Unit) {
        for (provider in Provider.entries) for (structured in listOf(false, true)) block(provider, structured)
    }

    suspend fun call(gateway: LlmGateway, structured: Boolean, recovery: RecoveryPolicy?): LlmGatewayResponse {
        val config = CompletionConfig(temperature = 0.25, reasoningEffort = ReasoningEffort.LOW, recovery = recovery)
        return if (structured) {
            gateway.completeJsonResponse("test-model", messages, schema, config)
        } else {
            gateway.complete("test-model", messages, listOf(countingTool {}), config)
        }
    }

    fun policy(
        events: MutableList<RecoveryEvent> = mutableListOf(),
        wires: MutableList<RecoveryWire> = mutableListOf(),
        sleeps: MutableList<Long> = mutableListOf(),
        wall: Long = 0,
    ): RecoveryPolicy = RecoveryPolicy(
        maxAttempts = 3,
        admission = { true },
        observer = { events += it },
        capture = { wires += it },
        sleep = { sleeps += it },
        clock = { wall },
        monotonicClock = { 0 },
        jitter = { it },
    )

    fun countingTool(execute: () -> Unit): LlmTool = object : LlmTool {
        override val descriptor = ToolDescriptor("count-tool", "counting tool", buildJsonObject { put("type", "object") })
        override suspend fun execute(arguments: JsonObject): String {
            execute()
            return "tool-result"
        }
    }

    enum class Provider {
        OLLAMA,
        OMLX,
        OPENAI,
        ;

        fun gateway(url: String, timeout: Duration? = null): OwnedGateway = if (this == OLLAMA) {
            val gateway = OllamaGateway(url)
            OwnedGateway(gateway, gateway::close)
        } else if (this == OPENAI) {
            val gateway = OpenAIGateway("credential-secret", url)
            OwnedGateway(gateway, gateway::close)
        } else {
            val gateway = OmlxGateway(url, apiKey = "credential-secret", timeout = timeout)
            OwnedGateway(gateway, gateway::close)
        }

        fun success(structured: Boolean): ScriptedReply {
            val content = if (structured) "{\"value\":\"ok\"}" else "ok"
            val encoded = Json.encodeToString(content)
            val body = if (this == OLLAMA) {
                """{"model":"test-model",
                    "message":{"role":"assistant","content":$encoded,"thinking":"native-reasoning"},
                    "done":true,"done_reason":"stop","eval_count":7}"""
            } else {
                """{"model":"test-model","choices":[{
                    "message":{"role":"assistant","content":$encoded,"reasoning_content":"native-reasoning"},
                    "finish_reason":"stop"}],"usage":{"completion_tokens":7}}"""
            }
            return ScriptedReply(200, body)
        }

        fun toolReply(): ScriptedReply = ScriptedReply(
            200,
            if (this == OLLAMA) {
                """{"message":{"role":"assistant","tool_calls":[{"function":{"name":"count-tool","arguments":{}}}]},"done":true}"""
            } else {
                """{"choices":[{"message":{"role":"assistant","tool_calls":[{
                    "id":"call-1","type":"function","function":{"name":"count-tool","arguments":"{}"}}]}}]}"""
            },
        )
    }

    class OwnedGateway(val value: LlmGateway, private val close: () -> Unit) : AutoCloseable {
        override fun close() = close.invoke()
    }

    const val TEST_TIMEOUT = 10_000L
    val messages = listOf(LlmMessage.system("system"), LlmMessage.assistant("prior-assistant"), LlmMessage.user("private-request ⚙️"))
    val schema = buildJsonObject {
        put("type", "object")
        put("description", "schema-value")
        put("properties", buildJsonObject { put("value", buildJsonObject { put("type", "string") }) })
    }

    fun assertAdmittedRequests(
        wires: List<RecoveryWire>,
        requests: List<ByteArray>,
        events: List<RecoveryEvent>,
        pendingFailure: RecoveryFailure,
    ) {
        val sent = wires.filter { it.inspectResponse() == null }
        assertEquals(listOf(1, 2), sent.map { it.identity.attemptNumber })
        assertEquals(1, sent.map { it.identity.logicalId }.distinct().size)
        assertEquals(2, sent.map { it.identity.attemptId }.distinct().size)
        sent.zip(requests).forEach { (wire, bytes) -> assertContentEquals(bytes, wire.inspectRequest()) }
        assertContentEquals(requests[0], requests[1])
        assertEquals(listOf(pendingFailure), events.last().failures)
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
    }
}
