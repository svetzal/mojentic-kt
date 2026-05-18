package com.mojentic.agents

import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmGatewayResponse
import com.mojentic.llm.MessageRole
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BaseAsyncLlmAgentTest {
    @Test
    fun generateResponsePrependsSystemBehaviour() = runTest {
        val gateway = AgentStubGateway(ArrayDeque(listOf(LlmGatewayResponse(content = "ack"))))
        val broker = LlmBroker(gateway)
        val agent = BaseAsyncLlmAgent(
            broker = broker,
            model = "test",
            behaviour = "You speak like a pirate.",
        )

        val response = agent.generateResponse("ahoy")

        assertEquals("ack", response.content)
        val messages = gateway.completeCalls.first()
        assertEquals(2, messages.size)
        assertEquals(MessageRole.System, messages[0].role)
        assertEquals("You speak like a pirate.", messages[0].content)
        assertEquals(MessageRole.User, messages[1].role)
        assertEquals("ahoy", messages[1].content)
    }

    @Test
    fun addToolMakesItAvailableToTheNextCall() = runTest {
        val gateway = AgentStubGateway(ArrayDeque(listOf(LlmGatewayResponse(content = "ok"))))
        val broker = LlmBroker(gateway)
        val agent = BaseAsyncLlmAgent(broker = broker, model = "test")

        val tool = StubAgentTool("greet", "say hi", "hi")
        agent.addTool(tool)
        agent.generateResponse("hello")

        val passedTools = gateway.completeTools.first()
        assertNotNull(passedTools)
        assertEquals(listOf("greet"), passedTools.map { it.name })
    }

    @Test
    fun toolsSnapshotIsImmutable() {
        val gateway = AgentStubGateway(ArrayDeque())
        val agent = BaseAsyncLlmAgent(broker = LlmBroker(gateway), model = "test")

        val before = agent.tools
        agent.addTool(StubAgentTool("a", "a", "a"))

        assertTrue(before.isEmpty())
        assertEquals(1, agent.tools.size)
    }
}
