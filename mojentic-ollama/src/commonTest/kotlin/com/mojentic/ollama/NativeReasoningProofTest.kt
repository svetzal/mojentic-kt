package com.mojentic.ollama

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.ReasoningEffort
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeReasoningProofTest {
    @Test
    fun ordinaryToolContinuationRetainsNativeAssistantAtHttpBoundary() = runTest {
        val requests = mutableListOf<JsonObject>()
        val engine = MockEngine { request ->
            requests += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            respond(
                if (requests.size == 1) {
                    """
                    {"message":{"role":"assistant","content":"visible-before-tool","thinking":"native-sentinel",
                    "tool_calls":[{"function":{"name":"lookup","arguments":{"key":"value"}}}]},"done":true}
                    """.trimIndent().replace("\n", "")
                } else {
                    """{"message":{"role":"assistant","content":"final-answer"},"done":true}"""
                },
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val tool = object : LlmTool {
            override val descriptor = ToolDescriptor("lookup", "Lookup", JsonObject(emptyMap()))
            override suspend fun execute(arguments: JsonObject): String {
                assertEquals(JsonPrimitive("value"), arguments["key"])
                return "tool-result-sentinel"
            }
        }
        val gateway = OllamaGateway(engine = engine)
        try {
            val response = LlmBroker(gateway).complete(
                "misleading-non-reasoning-name",
                listOf(LlmMessage.user("question")),
                listOf(tool),
                CompletionConfig(reasoningEffort = ReasoningEffort.LOW, preserveReasoning = true),
            )
            assertEquals("final-answer", response.content)
            val followup = requests[1].getValue("messages").jsonArray
            val assistant = followup[1].jsonObject
            assertEquals(JsonPrimitive("native-sentinel"), assistant["thinking"])
            assertEquals(JsonPrimitive("visible-before-tool"), assistant["content"])
            assertEquals(
                JsonPrimitive("lookup"),
                assistant.getValue("tool_calls").jsonArray[0].jsonObject["function"]?.jsonObject?.get("name"),
            )
            assertEquals(JsonPrimitive("tool-result-sentinel"), followup[2].jsonObject["content"])
            requests.forEach { assertEquals(JsonPrimitive("low"), it["think"]) }
        } finally {
            gateway.close()
        }
    }
}
