package com.mojentic.omlx

import com.mojentic.Mojentic
import com.mojentic.anthropic.AnthropicGateway
import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.ChatSession
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.ReasoningEffort
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import com.mojentic.ollama.OllamaGateway
import com.mojentic.openai.OpenAIGateway
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Public consumer of Maven artifacts only. Responses derive from the library's versioned contracts. */
class PackagedApiTest {
    private fun contract(name: String): JsonObject {
        val bytes = File(System.getProperty("contractDirectory"), "$name.json").readBytes()
        val hashes = mapOf(
            "native_reasoning_v1" to "d5539eb334b69e0f8b698e8cd2c1402772dfabd09af92c652e1d1dc35f34e127",
            "context_window_v1" to "9327502962112a020bafb46c6a677366df732f5a29cd44c92f17e8f632281395",
            "request_fidelity_v1" to "6f02088311f50707b9c12154d888f844928c23d359043cb2e5d5654bf7e5eaea",
        )
        assertEquals(hashes.getValue(name), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        return Json.parseToJsonElement(bytes.decodeToString()).jsonObject
    }

    @Test
    fun artifactVersionMatchesConsumerCoordinates() {
        assertEquals(System.getProperty("candidateVersion"), Mojentic.VERSION)
    }

    @Test
    fun nativeControlsAndRetentionSurvivePackagedSessionToolContinuation(): Unit = runBlocking {
        val shared = contract("native_reasoning_v1")
        for (provider in listOf("ollama", "omlx")) {
            val replies = shared.getValue("providers").jsonObject.getValue(provider).jsonObject
            RecoveryScriptedServer(
                listOf("ordinary_tool", "ordinary_final").map {
                    ScriptedReply(200, replies.getValue(it).jsonPrimitive.content)
                },
            ).use { server ->
                val gateway = if (provider == "ollama") OllamaGateway(server.url) else OmlxGateway(server.url, apiKey = "test")
                var executions = 0
                val tool = object : LlmTool {
                    override val descriptor = ToolDescriptor("lookup", "Lookup", JsonObject(emptyMap()))
                    override suspend fun execute(arguments: JsonObject): String {
                        executions++
                        return "packaged-tool-${arguments.getValue("key").jsonPrimitive.content}"
                    }
                }
                try {
                    val session = ChatSession(
                        LlmBroker(gateway),
                        "misleading-model",
                        tools = listOf(tool),
                        config = CompletionConfig(
                            reasoningEffort = ReasoningEffort.LOW,
                            preserveReasoning = true,
                            temperature = 0.0,
                            maxTokens = 37,
                        ),
                    )
                    val answer = session.send("question")
                    assertEquals("final-answer", answer.content)
                    assertEquals(2, executions)
                    val request = Json.parseToJsonElement(server.requests.last().decodeToString()).jsonObject
                    val messages = request.getValue("messages").jsonArray
                    val assistant = messages[1].jsonObject
                    assertEquals(
                        JsonPrimitive("native-before-tools"),
                        assistant[
                            if (provider ==
                                "ollama"
                            ) {
                                "thinking"
                            } else {
                                "reasoning_content"
                            },
                        ],
                    )
                    assertEquals(JsonPrimitive("visible-before-tools"), assistant["content"])
                    assertEquals(2, assistant.getValue("tool_calls").jsonArray.size)
                    assertEquals(
                        listOf("packaged-tool-alpha", "packaged-tool-beta"),
                        messages.drop(2).map {
                            it.jsonObject.getValue("content").jsonPrimitive.content
                        },
                    )
                    if (provider == "ollama") {
                        assertEquals(JsonPrimitive("low"), request["think"])
                        val options = request.getValue("options").jsonObject
                        assertEquals(JsonPrimitive(0.0), options["temperature"])
                        assertEquals(JsonPrimitive(37), options["num_predict"])
                        assertFalse("num_ctx" in options)
                    } else {
                        assertEquals(JsonPrimitive("low"), request["reasoning_effort"])
                        assertEquals(JsonPrimitive(true), request.getValue("chat_template_kwargs").jsonObject["preserve_thinking"])
                        assertEquals(JsonPrimitive(0.0), request["temperature"])
                        assertEquals(JsonPrimitive(37), request["max_tokens"])
                    }
                } finally {
                    when (gateway) {
                        is OllamaGateway -> gateway.close()
                        is OmlxGateway -> gateway.close()
                    }
                }
            }
        }
    }

    @Test
    fun packagedRemoteAdaptersRetryExactPayloadAndKeepUnsetFieldsAbsent(): Unit = runBlocking {
        val shared = contract("request_fidelity_v1")
        for (anthropic in listOf(false, true)) {
            val success = if (anthropic) {
                """
                {"id":"msg-test","type":"message","role":"assistant",
                "content":[{"type":"text","text":"answer"}],"stop_reason":"end_turn",
                "usage":{"input_tokens":1,"output_tokens":1}}
                """.trimIndent()
            } else {
                shared.getValue("responses").jsonObject.getValue("openai-complete").jsonPrimitive.content
            }
            RecoveryScriptedServer(listOf(ScriptedReply(503, "busy"), ScriptedReply(200, success))).use { server ->
                val gateway = if (anthropic) {
                    AnthropicGateway(
                        apiKey = "test",
                        host = server.url,
                    )
                } else {
                    OpenAIGateway("test", "${server.url}/v1")
                }
                try {
                    val response = gateway.complete(
                        "unknown",
                        listOf(LlmMessage.user("question")),
                        config = CompletionConfig(
                            recovery = RecoveryPolicy(maxAttempts = 2, sleep = {
                            }, jitter = { 0 }),
                        ),
                    )
                    assertEquals("answer", response.content)
                    assertEquals(2, server.requests.size)
                    assertContentEquals(server.requests[0], server.requests[1])
                    val request = Json.parseToJsonElement(server.requests[0].decodeToString()).jsonObject
                    assertFalse("temperature" in request)
                    if (anthropic) assertEquals(JsonPrimitive(4096), request["max_tokens"]) else assertFalse("max_tokens" in request)
                } finally {
                    when (gateway) {
                        is AnthropicGateway -> gateway.close()
                        is OpenAIGateway -> gateway.close()
                    }
                }
            }
        }
    }

    @Test
    fun packagedContextDiscoveryExecutesContractRows(): Unit = runBlocking {
        val shared = contract("context_window_v1")
        for (element in shared.getValue("cases").jsonArray) {
            val row = element.jsonObject
            val expected = row.getValue("expected").jsonObject
            RecoveryScriptedServer(
                listOf(ScriptedReply(row.getValue("status").jsonPrimitive.content.toInt(), row.getValue("response").jsonPrimitive.content)),
            ).use { server ->
                val provider = row.getValue("provider").jsonPrimitive.content
                val key = if (row["without_api_key"]?.jsonPrimitive?.content == "true") "" else "test"
                val gateway = if (provider == "ollama") OllamaGateway(server.url) else OmlxGateway(server.url, apiKey = key)
                try {
                    val model = row.getValue("model").jsonPrimitive.content
                    if ("error" in expected) {
                        assertFailsWith<LlmGatewayException> { gateway.modelContextWindow(model) }
                    } else if (expected["window"] is JsonObject) {
                        val window = gateway.modelContextWindow(model)
                        val value = expected.getValue("window").jsonObject
                        assertEquals(value.getValue("max_context_tokens").jsonPrimitive.content.toInt(), window?.maxContextTokens)
                        assertEquals(value.getValue("field").jsonPrimitive.content, window?.field)
                        assertEquals(value["model_num_ctx"]?.jsonPrimitive?.content?.toInt(), window?.modelNumCtx)
                    } else {
                        assertNull(gateway.modelContextWindow(model))
                    }
                    val wire = row.getValue("request").jsonObject
                    assertEquals(
                        "${wire.getValue("method").jsonPrimitive.content} ${wire.getValue("path").jsonPrimitive.content} HTTP/1.1",
                        server.requestLines.single(),
                    )
                    if (provider == "ollama") {
                        assertEquals(wire.getValue("body"), Json.parseToJsonElement(server.requests.single().decodeToString()))
                    } else {
                        assertTrue(server.requests.single().isEmpty())
                        assertEquals(if (key.isEmpty()) null else "Bearer $key", server.requestHeaders.single()["authorization"])
                    }
                } finally {
                    when (gateway) {
                        is OllamaGateway -> gateway.close()
                        is OmlxGateway -> gateway.close()
                    }
                }
            }
        }
    }

    @Test
    fun incompleteNativeStreamIsTerminalAndNeverConsumesRetrySentinel(): Unit = runBlocking {
        val shared = contract("native_reasoning_v1")
        val valid = shared.getValue("providers").jsonObject.getValue("omlx").jsonObject.getValue("stream_final").jsonPrimitive.content
        val incomplete = "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"partial-native\"}}]}\n\n"
        RecoveryScriptedServer(listOf(ScriptedReply(200, incomplete), ScriptedReply(200, valid))).use { server ->
            val gateway = OmlxGateway(server.url, apiKey = "test")
            try {
                val events = gateway.streamEvents(
                    "unknown",
                    listOf(LlmMessage.user("question")),
                    CompletionConfig(
                        recovery = RecoveryPolicy(maxAttempts = 2, admission = {
                            error("partial delivery cannot admit retry")
                        }),
                    ),
                ).toList()
                assertIs<CompletionStreamEvent.Error>(events.last())
                assertTrue(events.none { it is CompletionStreamEvent.Completed })
                assertEquals(1, server.requests.size)
            } finally {
                gateway.close()
            }
        }
    }

    @Test
    fun allPackagedAdaptersHonorObsoleteRetryAfterMinimums(): Unit = runBlocking {
        val native = contract("native_reasoning_v1").getValue("providers").jsonObject
        val openai = contract("request_fidelity_v1").getValue("responses").jsonObject.getValue("openai-complete").jsonPrimitive.content
        for (provider in listOf("ollama", "omlx", "openai", "anthropic")) {
            val success = when (provider) {
                "ollama", "omlx" -> native.getValue(provider).jsonObject.getValue("ordinary_final").jsonPrimitive.content

                "anthropic" -> """
                {"id":"msg-test","type":"message","role":"assistant",
                "content":[{"type":"text","text":"answer"}],"stop_reason":"end_turn",
                "usage":{"input_tokens":1,"output_tokens":1}}
                """.trimIndent()

                else -> openai
            }
            for (date in listOf("Thu, 01 Jan 1970 00:00:03 GMT", "Thursday, 01-Jan-70 00:00:03 GMT", "Thu Jan  1 00:00:03 1970")) {
                RecoveryScriptedServer(
                    listOf(ScriptedReply(429, "busy", mapOf("Retry-After" to date)), ScriptedReply(200, success)),
                ).use { server ->
                    val gateway = when (provider) {
                        "ollama" -> OllamaGateway(server.url)
                        "omlx" -> OmlxGateway(server.url, apiKey = "test")
                        "anthropic" -> AnthropicGateway("test", server.url)
                        else -> OpenAIGateway("test", "${server.url}/v1")
                    }
                    val delays = mutableListOf<Long>()
                    try {
                        gateway.complete(
                            "unknown",
                            listOf(LlmMessage.user("question")),
                            config = CompletionConfig(
                                recovery = RecoveryPolicy(
                                    maxAttempts = 2,
                                    admission = { true },
                                    baseDelayMillis = 0,
                                    delayCeilingMillis = 5000,
                                    clock = { 0 },
                                    sleep = { delays += it },
                                    jitter = { 0 },
                                ),
                            ),
                        )
                        assertEquals(listOf(3000L), delays, "$provider/$date")
                        assertEquals(2, server.requests.size)
                        assertContentEquals(server.requests[0], server.requests[1])
                    } finally {
                        when (gateway) {
                            is OllamaGateway -> gateway.close()
                            is OmlxGateway -> gateway.close()
                            is AnthropicGateway -> gateway.close()
                            is OpenAIGateway -> gateway.close()
                        }
                    }
                }
            }
        }
    }
}
