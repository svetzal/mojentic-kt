package com.mojentic.openai

import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.StreamEvent
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import com.mojentic.omlx.OmlxGateway
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class SplitToolCallRoundTripTest {
    @Test
    fun openAIStreamKeepsFirstChunkIdInFollowUpToolMessage() = runTest {
        roundTrip(omlx = false)
    }

    @Test
    fun omlxStreamKeepsFirstChunkIdInFollowUpToolMessage() = runTest {
        roundTrip(omlx = true)
    }

    private suspend fun roundTrip(omlx: Boolean) {
        val requests = mutableListOf<JsonObject>()
        val engine = MockEngine { request ->
            requests += Json.parseToJsonElement((request.body as io.ktor.http.content.TextContent).text).jsonObject
            val response = if (requests.size == 1) {
                """
                data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_split","type":"function","function":{"name":"lookup","arguments":""}}]}}]}

                data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"q\":"}}]}}]}

                data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"today\"}"}}]},"finish_reason":"tool_calls"}]}

                data: [DONE]

                """.trimIndent() + "\n\n"
            } else {
                """data: {"choices":[{"index":0,"delta":{"content":"answer"},"finish_reason":"stop"}]}""" +
                    "\n\ndata: [DONE]\n\n"
            }
            respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }
        val tool = object : LlmTool {
            override val descriptor = ToolDescriptor("lookup", "Look up a date", buildJsonObject { put("type", "object") })

            override suspend fun execute(arguments: JsonObject): String {
                assertEquals(buildJsonObject { put("q", "today") }, arguments)
                return "found"
            }
        }
        val gateway = if (omlx) OmlxGateway(engine = engine) else OpenAIGateway(apiKey = "test", engine = engine)
        try {
            val events = LlmBroker(gateway).stream("model", listOf(LlmMessage.user("look up today")), listOf(tool)).toList()
            assertEquals("call_split", events.filterIsInstance<StreamEvent.ToolCall>().single().call.id)
            assertEquals("answer", events.filterIsInstance<StreamEvent.TextChunk>().joinToString("") { it.text })
            assertEquals(2, requests.size)
            val sent = requests[1].getValue("messages").jsonArray.map { it.jsonObject }
            val toolMessage = sent.single { it["role"] == JsonPrimitive("tool") }
            assertEquals(JsonPrimitive("call_split"), toolMessage["tool_call_id"])
            assertEquals(JsonPrimitive("found"), toolMessage["content"])
            val assistant = sent.single { it["role"] == JsonPrimitive("assistant") }
            assertEquals(JsonPrimitive("call_split"), assistant.getValue("tool_calls").jsonArray.single().jsonObject["id"])
        } finally {
            when (gateway) {
                is OpenAIGateway -> gateway.close()
                is OmlxGateway -> gateway.close()
            }
        }
    }
}
