package com.mojentic.anthropic

import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.ImageContent
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.LlmToolCall
import com.mojentic.llm.MessageRole
import com.mojentic.llm.TextContent
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.ktor.http.content.TextContent as KtorTextContent

class AnthropicGatewayTest {
    private lateinit var gateway: AnthropicGateway
    private var lastRequestBody: String? = null
    private var lastRequestHeaders: Map<String, List<String>> = emptyMap()

    @AfterTest
    fun tearDown() {
        if (::gateway.isInitialized) gateway.close()
    }

    private fun mockEngine(body: String, status: HttpStatusCode = HttpStatusCode.OK): MockEngine = MockEngine { request ->
        lastRequestBody = (request.body as? KtorTextContent)?.text
        lastRequestHeaders = request.headers.toMap()
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    @Test
    fun completeParsesAssistantText() = runTest {
        gateway = AnthropicGateway(
            apiKey = "test",
            engine = mockEngine(
                """
                {"id":"m_1","role":"assistant",
                 "content":[{"type":"text","text":"hello back"}],
                 "stop_reason":"end_turn",
                 "usage":{"input_tokens":5,"output_tokens":7}}
                """.trimIndent().replace("\n", ""),
            ),
        )

        val response = gateway.complete(
            model = "claude-3-5-sonnet-latest",
            messages = listOf(LlmMessage.system("be brief"), LlmMessage.user("hi")),
        )

        assertEquals("hello back", response.content)
        assertTrue(response.toolCalls.isEmpty())
        assertNull(response.thinking)
    }

    @Test
    fun completeAppliesSystemAndApiHeaders() = runTest {
        gateway = AnthropicGateway(
            apiKey = "sekret",
            engine = mockEngine(
                """{"id":"m_1","content":[{"type":"text","text":"ok"}]}""",
            ),
        )

        gateway.complete(
            model = "claude-3-5-haiku-latest",
            messages = listOf(LlmMessage.system("rules"), LlmMessage.user("hi")),
        )

        val body = assertNotNull(lastRequestBody)
        assertContains(body, "\"system\":\"rules\"")
        assertContains(body, "\"role\":\"user\"")
        assertEquals(listOf("sekret"), lastRequestHeaders["x-api-key"])
        assertEquals(listOf(DEFAULT_ANTHROPIC_VERSION), lastRequestHeaders["anthropic-version"])
    }

    @Test
    fun completeSurfacesToolCalls() = runTest {
        gateway = AnthropicGateway(
            apiKey = "test",
            engine = mockEngine(
                """
                {"id":"m_2","role":"assistant",
                 "content":[
                   {"type":"text","text":"calling tool now"},
                   {"type":"tool_use","id":"tu_1","name":"do","input":{"a":"b"}}
                 ],
                 "stop_reason":"tool_use"}
                """.trimIndent().replace("\n", ""),
            ),
        )

        val response = gateway.complete(
            model = "claude-3-5-sonnet-latest",
            messages = listOf(LlmMessage.user("kick it off")),
            tools = listOf(stubTool()),
        )

        assertEquals("calling tool now", response.content)
        assertEquals(1, response.toolCalls.size)
        assertEquals("do", response.toolCalls[0].name)
        assertEquals("tu_1", response.toolCalls[0].id)
        assertEquals("b", (response.toolCalls[0].arguments["a"] as JsonPrimitive).content)
        val body = assertNotNull(lastRequestBody)
        assertContains(body, "\"tools\":")
        assertContains(body, "\"input_schema\":")
    }

    @Test
    fun completeFlattensThinkingContent() = runTest {
        gateway = AnthropicGateway(
            apiKey = "test",
            engine = mockEngine(
                """
                {"id":"m_3","role":"assistant",
                 "content":[
                   {"type":"thinking","thinking":"reasoning step"},
                   {"type":"text","text":"answer"}
                 ]}
                """.trimIndent().replace("\n", ""),
            ),
        )

        val response = gateway.complete(
            model = "claude-3-5-sonnet-latest",
            messages = listOf(LlmMessage.user("ponder")),
        )

        assertEquals("answer", response.content)
        assertEquals("reasoning step", response.thinking)
    }

    @Test
    fun completeJsonReturnsForcedToolInput() = runTest {
        gateway = AnthropicGateway(
            apiKey = "test",
            engine = mockEngine(
                """
                {"id":"m_4","role":"assistant",
                 "content":[
                   {"type":"tool_use","id":"tu_x","name":"respond_in_json","input":{"answer":"42"}}
                 ],
                 "stop_reason":"tool_use"}
                """.trimIndent().replace("\n", ""),
            ),
        )

        val schema = buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put("answer", buildJsonObject { put("type", "string") })
                },
            )
        }
        val obj = gateway.completeJson(
            model = "claude-3-5-sonnet-latest",
            messages = listOf(LlmMessage.user("give me the answer")),
            schema = schema,
        )

        assertEquals("42", (obj["answer"] as JsonPrimitive).content)
        val body = assertNotNull(lastRequestBody)
        assertContains(body, "\"tool_choice\":{\"type\":\"tool\",\"name\":\"respond_in_json\"}")
    }

    @Test
    fun completeJsonThrowsWhenForcedToolMissing() = runTest {
        gateway = AnthropicGateway(
            apiKey = "test",
            engine = mockEngine(
                """{"id":"m_5","content":[{"type":"text","text":"hi"}]}""",
            ),
        )

        assertFailsWith<LlmGatewayException> {
            gateway.completeJson(
                model = "claude-3-5-sonnet-latest",
                messages = listOf(LlmMessage.user("hi")),
                schema = buildJsonObject { put("type", "object") },
            )
        }
    }

    @Test
    fun multimodalUserMessageEncodesBase64Image() = runTest {
        gateway = AnthropicGateway(
            apiKey = "test",
            engine = mockEngine("""{"id":"m_6","content":[{"type":"text","text":"ok"}]}"""),
        )

        gateway.complete(
            model = "claude-3-5-sonnet-latest",
            messages = listOf(
                LlmMessage.user(
                    parts = listOf(
                        TextContent("describe this"),
                        ImageContent(data = "QUFB", mimeType = "image/png"),
                    ),
                ),
            ),
        )

        val body = assertNotNull(lastRequestBody)
        assertContains(body, "\"type\":\"image\"")
        assertContains(body, "\"media_type\":\"image/png\"")
        assertContains(body, "\"data\":\"QUFB\"")
    }

    @Test
    fun toolResultMessagesBecomeUserRoleToolResultBlocks() = runTest {
        gateway = AnthropicGateway(
            apiKey = "test",
            engine = mockEngine("""{"id":"m_7","content":[{"type":"text","text":"thanks"}]}"""),
        )

        val toolCall = LlmToolCall(id = "tu_77", name = "lookup", arguments = buildJsonObject {})
        gateway.complete(
            model = "claude-3-5-sonnet-latest",
            messages = listOf(
                LlmMessage.user("look it up"),
                LlmMessage.assistant(content = null, toolCalls = listOf(toolCall)),
                LlmMessage(role = MessageRole.Tool, content = "42", toolCalls = listOf(toolCall)),
            ),
        )

        val body = assertNotNull(lastRequestBody)
        assertContains(body, "\"type\":\"tool_use\"")
        assertContains(body, "\"type\":\"tool_result\"")
        assertContains(body, "\"tool_use_id\":\"tu_77\"")
    }

    @Test
    fun availableModelsSortsAlphabetically() = runTest {
        gateway = AnthropicGateway(
            apiKey = "test",
            engine = mockEngine("""{"data":[{"id":"claude-zeta"},{"id":"claude-alpha"},{"id":"claude-mid"}]}"""),
        )

        val models = gateway.availableModels()

        assertEquals(listOf("claude-alpha", "claude-mid", "claude-zeta"), models)
    }

    @Test
    fun errorResponseSurfacesMessage() = runTest {
        gateway = AnthropicGateway(
            apiKey = "test",
            engine = mockEngine(
                body = """{"type":"error","error":{"type":"invalid_request_error","message":"missing model"}}""",
                status = HttpStatusCode.BadRequest,
            ),
        )

        val ex = assertFailsWith<LlmGatewayException> {
            gateway.complete(
                model = "claude-3-5-sonnet-latest",
                messages = listOf(LlmMessage.user("hi")),
            )
        }
        assertContains(ex.message ?: "", "missing model")
    }

    @Test
    fun reasoningEffortIsLoggedNotForwarded() = runTest {
        gateway = AnthropicGateway(
            apiKey = "test",
            engine = mockEngine("""{"id":"m_8","content":[{"type":"text","text":"ok"}]}"""),
        )

        gateway.complete(
            model = "claude-3-5-sonnet-latest",
            messages = listOf(LlmMessage.user("hi")),
            config = CompletionConfig(reasoningEffort = com.mojentic.llm.ReasoningEffort.HIGH),
        )

        val body = assertNotNull(lastRequestBody)
        // Anthropic does not yet accept reasoning_effort on the wire; we must not forward it.
        assertTrue(!body.contains("reasoning_effort"), "should not forward reasoning_effort: $body")
    }

    @Test
    fun maxTokensFallsBackToDefaultWhenConfigIsZero() = runTest {
        gateway = AnthropicGateway(
            apiKey = "test",
            engine = mockEngine("""{"id":"m_9","content":[{"type":"text","text":"ok"}]}"""),
        )

        gateway.complete(
            model = "claude-3-5-sonnet-latest",
            messages = listOf(LlmMessage.user("hi")),
            config = CompletionConfig(maxTokens = 0),
        )

        val body = assertNotNull(lastRequestBody)
        assertContains(body, "\"max_tokens\":${AnthropicGateway.DEFAULT_MAX_TOKENS}")
    }

    private fun stubTool(): LlmTool = object : LlmTool {
        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "do",
            description = "do a thing",
            parameters = buildJsonObject {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put("a", buildJsonObject { put("type", "string") })
                    },
                )
            },
        )

        override suspend fun execute(arguments: kotlinx.serialization.json.JsonObject): String = ""
    }
}

private fun io.ktor.http.Headers.toMap(): Map<String, List<String>> = entries().associate { it.key to it.value }
