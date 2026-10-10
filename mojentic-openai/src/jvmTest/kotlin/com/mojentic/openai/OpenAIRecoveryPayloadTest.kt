package com.mojentic.openai

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.ReasoningEffort
import com.mojentic.llm.recovery.RecoveryEvent
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.recovery.RecoveryStage
import com.mojentic.llm.recovery.RecoveryWire
import com.mojentic.omlx.RecoveryScriptedServer
import com.mojentic.omlx.RecoveryTestFixtures
import com.mojentic.omlx.ScriptedReply
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Golden semantic bytes through the default production client, including model-specific controls. */
class OpenAIRecoveryPayloadTest {
    @Test
    fun supportedPayloadIsFrozenBeforeAdmissionAcrossAllEntrypoints(): Unit = runBlocking {
        for (model in listOf("gpt-4o", "o3")) {
            for (operation in listOf("complete", "structured", "stream", "streamEvents")) {
                provePayload(model, operation)
            }
        }
    }

    private suspend fun provePayload(model: String, operation: String) {
        val streaming = operation.startsWith("stream")
        val structured = operation == "structured"
        val hasTools = operation == "complete" || operation == "stream"
        val success = successReply(model, streaming, structured)
        val replies = listOf(ScriptedReply(503, "private-busy", mapOf("Retry-After" to "0")), success)
        val wires = mutableListOf<RecoveryWire>()
        val events = mutableListOf<RecoveryEvent>()
        val messages = mutableListOf(LlmMessage.system("system"), LlmMessage.assistant("prior"), LlmMessage.user("private-é"))
        val config = CompletionConfig(
            temperature = 0.25,
            maxTokens = 17,
            reasoningEffort = ReasoningEffort.LOW,
            recovery = RecoveryPolicy(
                maxAttempts = 2,
                admission = {
                    messages += LlmMessage.user("mutation-after-encoding")
                    true
                },
                jitter = { 0 },
                observer = { events += it },
                capture = { wires += it },
            ),
        )
        RecoveryScriptedServer(replies).use { server ->
            val gateway = OpenAIGateway("credential-secret", server.url)
            try {
                val tools = listOf(RecoveryTestFixtures.countingTool {})
                when (operation) {
                    "complete" -> gateway.complete(model, messages, tools, config)

                    "structured" -> gateway.completeJsonResponse(model, messages, buildJsonObject { put("type", "object") }, config)

                    "stream" -> gateway.stream(model, messages, tools, config).collect {}

                    else -> {
                        val output = mutableListOf<CompletionStreamEvent>()
                        gateway.streamEvents(model, messages, config).collect { output += it }
                        val terminal = assertIs<CompletionStreamEvent.Completed>(output.last())
                        assertEquals("stop", terminal.evidence.finishReason)
                        assertEquals(model, terminal.evidence.providerModel)
                        assertEquals(buildJsonObject { put("completion_tokens", 7) }, terminal.evidence.usage)
                    }
                }
                assertPayload(
                    expectedPayload(model, streaming, structured, hasTools).encodeToByteArray(),
                    server.requests,
                    wires,
                    events,
                    replies,
                )
            } finally {
                gateway.close()
            }
        }
    }

    private fun assertPayload(
        expected: ByteArray,
        requests: List<ByteArray>,
        wires: List<RecoveryWire>,
        events: List<RecoveryEvent>,
        replies: List<ScriptedReply>,
    ) {
        assertEquals(2, requests.size)
        requests.forEach { assertContentEquals(expected, it) }
        val sent = wires.filter { it.inspectResponse() == null }
        assertEquals(listOf(1, 2), sent.map { it.identity.attemptNumber })
        assertEquals(1, sent.map { it.identity.logicalId }.distinct().size)
        assertEquals(2, sent.map { it.identity.attemptId }.distinct().size)
        wires.forEach { assertContentEquals(expected, it.inspectRequest()) }
        wires.filter { it.complete }.zip(replies).forEach { (wire, reply) ->
            assertContentEquals(reply.body.encodeToByteArray(), wire.inspectResponse())
        }
        assertEquals(listOf(503, 200), wires.filter { it.complete }.map { it.status })
        assertEquals(RecoveryStage.SUCCEEDED, events.last().stage)
        assertEquals(2, events.last().wireAttempts)
        assertEquals(events.first { it.stage == RecoveryStage.FAILED }.failures, events.last().failures)
    }

    private fun successReply(model: String, streaming: Boolean, structured: Boolean): ScriptedReply = if (streaming) {
        ScriptedReply(
            200,
            "data: {\"model\":\"$model\",\"choices\":[{\"delta\":{\"content\":\"ok-é\"}," +
                "\"finish_reason\":\"stop\"}],\"usage\":{\"completion_tokens\":7}}\n\ndata: [DONE]\n\n",
        )
    } else {
        RecoveryTestFixtures.Provider.OPENAI.success(structured)
    }

    private fun expectedPayload(model: String, streaming: Boolean, structured: Boolean, tools: Boolean): String {
        val controls = if (model == "o3") "\"max_completion_tokens\":17" else "\"temperature\":0.25,\"max_tokens\":17"
        val toolBytes = if (tools) {
            ",\"tools\":[{\"type\":\"function\",\"function\":{\"name\":\"count-tool\",\"description\":\"counting tool\"," +
                "\"parameters\":{\"type\":\"object\"}}}]"
        } else {
            ""
        }
        val format = if (structured) {
            ",\"response_format\":{\"type\":\"json_schema\",\"json_schema\":{\"name\":\"structured_response\"," +
                "\"schema\":{\"type\":\"object\"},\"strict\":false}}"
        } else {
            ""
        }
        return "{\"model\":\"$model\",\"messages\":[{\"role\":\"system\",\"content\":\"system\"}," +
            "{\"role\":\"assistant\",\"content\":\"prior\"}," +
            "{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"private-é\"}]}],$controls" +
            (if (streaming) ",\"stream\":true" else "") + toolBytes + format +
            (if (model == "o3") ",\"reasoning_effort\":\"low\"" else "") +
            (if (streaming) ",\"stream_options\":{\"include_usage\":true}" else "") + "}"
    }
}
