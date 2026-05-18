package com.mojentic.llm.tools

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class StubGateway(
    private val nextResponse: String = "",
) : UserInteractionGateway {
    val told: MutableList<String> = mutableListOf()
    val asked: MutableList<String> = mutableListOf()

    override suspend fun tell(message: String) {
        told += message
    }

    override suspend fun ask(request: String): String {
        asked += request
        return nextResponse
    }
}

class UserToolsTest {
    @Test
    fun askUserPassesPromptAndReturnsAnswer() = runTest {
        val gateway = StubGateway(nextResponse = "Paris")
        val tool = AskUserTool(gateway)

        val result = tool.execute(
            buildJsonObject { put("user_request", JsonPrimitive("Capital of France?")) },
        )

        val payload = Json.parseToJsonElement(result) as JsonObject
        assertEquals("Paris", payload["user_response"]!!.jsonPrimitive.content)
        assertEquals(listOf("Capital of France?"), gateway.asked)
    }

    @Test
    fun askUserMissingArgumentRaises() = runTest {
        val tool = AskUserTool(StubGateway())

        assertFailsWith<IllegalStateException> {
            tool.execute(JsonObject(emptyMap()))
        }
    }

    @Test
    fun tellUserDeliversMessageAndReturnsAck() = runTest {
        val gateway = StubGateway()
        val tool = TellUserTool(gateway)

        val result = tool.execute(
            buildJsonObject { put("message", JsonPrimitive("processing your request")) },
        )

        val payload = Json.parseToJsonElement(result) as JsonObject
        assertEquals("delivered", payload["status"]!!.jsonPrimitive.content)
        assertEquals(listOf("processing your request"), gateway.told)
    }

    @Test
    fun askUserDescriptorMatchesContract() {
        val tool = AskUserTool(StubGateway())

        assertEquals("ask_user", tool.name)
        assertTrue("user_request" in tool.descriptor.parameters.toString())
    }

    @Test
    fun tellUserDescriptorMatchesContract() {
        val tool = TellUserTool(StubGateway())

        assertEquals("tell_user", tool.name)
        assertTrue("message" in tool.descriptor.parameters.toString())
    }
}
