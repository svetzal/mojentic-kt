package com.mojentic.ollama

import com.mojentic.llm.LlmMessage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OllamaGatewayTest {
    private lateinit var gateway: OllamaGateway

    @AfterTest
    fun tearDown() {
        if (::gateway.isInitialized) gateway.close()
    }

    @Test
    fun completeParsesAssistantContent() = runTest {
        val engine = MockEngine { request ->
            assertEquals("http://localhost:11434/api/chat", request.url.toString())
            respond(
                content = """{"message":{"role":"assistant","content":"hi back"},"done":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        gateway = OllamaGateway(engine = engine)

        val response = gateway.complete(
            model = "qwen2.5:7b",
            messages = listOf(LlmMessage.user("hi")),
        )

        assertEquals("hi back", response.content)
        assertTrue(response.toolCalls.isEmpty())
    }

    @Test
    fun completeSurfacesToolCalls() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {"message":{"role":"assistant","content":null,
                     "tool_calls":[{"function":{"name":"do","arguments":{"a":"b"}}}]},
                     "done":true}
                """.trimIndent().replace("\n", ""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        gateway = OllamaGateway(engine = engine)

        val response = gateway.complete(model = "any", messages = listOf(LlmMessage.user("hi")))

        assertEquals(1, response.toolCalls.size)
        assertEquals("do", response.toolCalls[0].name)
        assertEquals("b", (response.toolCalls[0].arguments["a"] as JsonPrimitive).content)
    }

    @Test
    fun availableModelsSortsAlphabetically() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"models":[{"model":"zeta"},{"model":"alpha"},{"model":"mid"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        gateway = OllamaGateway(engine = engine)

        val models = gateway.availableModels()

        assertEquals(listOf("alpha", "mid", "zeta"), models)
    }

    @Test
    fun completeJsonReturnsParsedObject() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"message":{"role":"assistant","content":"{\"text\":\"hi\"}"},"done":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        gateway = OllamaGateway(engine = engine)

        val obj = gateway.completeJson(
            model = "any",
            messages = listOf(LlmMessage.user("greet")),
            schema = buildJsonObject { put("type", JsonPrimitive("object")) },
        )

        assertEquals("hi", (obj["text"] as JsonPrimitive).content)
    }
}
