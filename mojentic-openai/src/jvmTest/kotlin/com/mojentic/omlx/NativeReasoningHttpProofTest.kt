package com.mojentic.omlx

import com.mojentic.llm.ChatSession
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.ReasoningEffort
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeReasoningHttpProofTest {
    @Test
    fun completedAssistantReasoningSurvivesToolAndNextSessionTurn() = runTest {
        val replies = listOf(
            ScriptedReply(
                200,
                """
                {"message":{"role":"assistant","content":"visible-before-tool","thinking":"native-sentinel",
                "tool_calls":[{"function":{"name":"lookup","arguments":{"key":"value"}}}]},"done":true}
                """.trimIndent().replace("\n", ""),
            ),
            ScriptedReply(200, """{"message":{"role":"assistant","content":"final-answer","thinking":"final-native"},"done":true}"""),
            ScriptedReply(200, """{"message":{"role":"assistant","content":"next-answer"},"done":true}"""),
        )
        val tool = object : LlmTool {
            override val descriptor = ToolDescriptor("lookup", "Lookup", JsonObject(emptyMap()))
            override suspend fun execute(arguments: JsonObject): String {
                assertEquals(JsonPrimitive("value"), arguments["key"])
                return "tool-result-sentinel"
            }
        }
        RecoveryScriptedServer(replies).use { server ->
            val gateway = OllamaGateway(server.url)
            try {
                val session = ChatSession(
                    LlmBroker(gateway),
                    "misleading-model",
                    tools = listOf(tool),
                    config = CompletionConfig(reasoningEffort = ReasoningEffort.LOW, preserveReasoning = true),
                )
                assertEquals("final-answer", session.send("question").content)
                session.send("follow-up")
                val requests = server.requests.map { Json.parseToJsonElement(it.decodeToString()).jsonObject }
                val messages = requests[1].getValue("messages").jsonArray
                assertEquals(JsonPrimitive("native-sentinel"), messages[1].jsonObject["thinking"])
                assertEquals(JsonPrimitive("visible-before-tool"), messages[1].jsonObject["content"])
                assertEquals(JsonPrimitive("tool-result-sentinel"), messages[2].jsonObject["content"])
                val next = requests[2].getValue("messages").jsonArray
                assertEquals(messages, kotlinx.serialization.json.JsonArray(next.take(3)))
                assertEquals(JsonPrimitive("final-native"), next[3].jsonObject["thinking"])
                requests.forEach { assertEquals(JsonPrimitive("low"), it["think"]) }
            } finally {
                gateway.close()
            }
        }
    }
}
