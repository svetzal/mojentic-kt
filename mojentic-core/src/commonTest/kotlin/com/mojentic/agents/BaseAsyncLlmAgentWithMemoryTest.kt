package com.mojentic.agents

import com.mojentic.context.SharedWorkingMemory
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmGatewayResponse
import com.mojentic.llm.MessageRole
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaseAsyncLlmAgentWithMemoryTest {
    @Test
    fun injectsMemorySnapshotIntoPrompt() = runTest {
        val gateway = AgentStubGateway(ArrayDeque(listOf(LlmGatewayResponse(content = "ok"))))
        val memory = SharedWorkingMemory(mapOf("user_name" to JsonPrimitive("ada")))
        val agent = BaseAsyncLlmAgentWithMemory(
            broker = LlmBroker(gateway),
            model = "test",
            memory = memory,
            behaviour = "Helpful.",
            instructions = "Greet the user.",
        )

        agent.generateResponse("hello")

        val messages = gateway.completeCalls.first()
        assertEquals(MessageRole.System, messages[0].role)
        assertEquals("Helpful.", messages[0].content)
        assertEquals(MessageRole.User, messages[1].role)
        assertTrue(messages[1].content!!.contains("ada"))
        assertEquals(MessageRole.User, messages[2].role)
        assertEquals("Greet the user.", messages[2].content)
        assertEquals(MessageRole.User, messages[3].role)
        assertEquals("hello", messages[3].content)
    }

    @Test
    fun mergeMemoryUpdatesSharedMemory() = runTest {
        val memory = SharedWorkingMemory()
        val agent = BaseAsyncLlmAgentWithMemory(
            broker = LlmBroker(AgentStubGateway(ArrayDeque())),
            model = "test",
            memory = memory,
            behaviour = "b",
            instructions = "i",
        )

        agent.mergeMemory(mapOf("topic" to JsonPrimitive("greetings")))

        assertEquals(JsonPrimitive("greetings"), memory.getWorkingMemory()["topic"])
    }
}
