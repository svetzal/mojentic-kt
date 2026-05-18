package com.mojentic.openai

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.ImageContent
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.ReasoningEffort
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenAIGatewayTest {
    private lateinit var gateway: OpenAIGateway
    private val captured = mutableListOf<String>()

    @AfterTest
    fun tearDown() {
        if (::gateway.isInitialized) gateway.close()
    }

    private fun mock(body: String, status: HttpStatusCode = HttpStatusCode.OK): MockEngine = MockEngine { request: HttpRequestData ->
        captured += request.body.toString()
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    @Test
    fun completeParsesAssistantContent() = runTest {
        gateway = OpenAIGateway(
            apiKey = "test",
            engine = mock(
                """{"choices":[{"index":0,"message":{"role":"assistant","content":"hi back"}}]}""",
            ),
        )

        val response = gateway.complete(
            model = "gpt-4o-mini",
            messages = listOf(LlmMessage.user("hi")),
        )

        assertEquals("hi back", response.content)
        assertTrue(response.toolCalls.isEmpty())
    }

    @Test
    fun completeSurfacesToolCalls() = runTest {
        gateway = OpenAIGateway(
            apiKey = "test",
            engine = mock(
                """
                {"choices":[{"index":0,"message":{
                    "role":"assistant","content":null,
                    "tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"do","arguments":"{\"a\":\"b\"}"}}]
                }}]}
                """.trimIndent().replace("\n", ""),
            ),
        )

        val response = gateway.complete(
            model = "gpt-4o-mini",
            messages = listOf(LlmMessage.user("hi")),
        )

        assertEquals(1, response.toolCalls.size)
        assertEquals("do", response.toolCalls[0].name)
        assertEquals("call_1", response.toolCalls[0].id)
        assertEquals("b", (response.toolCalls[0].arguments["a"] as JsonPrimitive).content)
    }

    @Test
    fun availableModelsSortsAlphabetically() = runTest {
        gateway = OpenAIGateway(
            apiKey = "test",
            engine = mock("""{"data":[{"id":"zeta"},{"id":"alpha"},{"id":"mid"}]}"""),
        )

        val models = gateway.availableModels()

        assertEquals(listOf("alpha", "mid", "zeta"), models)
    }

    @Test
    fun completeJsonReturnsParsedObject() = runTest {
        gateway = OpenAIGateway(
            apiKey = "test",
            engine = mock(
                """{"choices":[{"index":0,"message":{"role":"assistant","content":"{\"text\":\"hi\"}"}}]}""",
            ),
        )

        val obj = gateway.completeJson(
            model = "gpt-4o-mini",
            messages = listOf(LlmMessage.user("greet")),
            schema = buildJsonObject { put("type", JsonPrimitive("object")) },
        )

        assertEquals("hi", (obj["text"] as JsonPrimitive).content)
    }

    @Test
    fun multimodalMessageIsSerialisedAsContentArray() = runTest {
        var requestBodyText: String? = null
        val engine = MockEngine { request ->
            requestBodyText = (request.body as? io.ktor.http.content.TextContent)?.text
            respond(
                content = """{"choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        gateway = OpenAIGateway(apiKey = "test", engine = engine)

        gateway.complete(
            model = "gpt-4o",
            messages = listOf(
                LlmMessage.user(
                    parts = listOf(
                        com.mojentic.llm.TextContent("describe this"),
                        ImageContent(data = "AAAA", mimeType = "image/png"),
                    ),
                ),
            ),
        )

        val body = assertNotNull(requestBodyText)
        assertTrue(body.contains("\"type\":\"image_url\""), "expected image_url part in body: $body")
        assertTrue(body.contains("data:image/png;base64,AAAA"), "expected data URL in body: $body")
    }

    @Test
    fun reasoningEffortUsesMaxCompletionTokensForOSeriesModels() = runTest {
        var requestBodyText: String? = null
        val engine = MockEngine { request ->
            requestBodyText = (request.body as? io.ktor.http.content.TextContent)?.text
            respond(
                content = """{"choices":[{"index":0,"message":{"role":"assistant","content":"done"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        gateway = OpenAIGateway(apiKey = "test", engine = engine)

        gateway.complete(
            model = "o3-mini",
            messages = listOf(LlmMessage.user("solve")),
            config = CompletionConfig(reasoningEffort = ReasoningEffort.HIGH),
        )

        val body = assertNotNull(requestBodyText)
        assertTrue(body.contains("\"max_completion_tokens\""), "expected max_completion_tokens in body: $body")
        assertTrue(body.contains("\"reasoning_effort\":\"high\""), "expected reasoning_effort=high in body: $body")
        assertTrue(!body.contains("\"temperature\""), "expected temperature omitted for reasoning model: $body")
    }
}
