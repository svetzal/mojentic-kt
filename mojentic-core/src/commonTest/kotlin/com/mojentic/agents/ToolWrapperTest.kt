package com.mojentic.agents

import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmGatewayResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ToolWrapperTest {
    @Test
    fun delegatesToWrappedAgent() = runTest {
        val gateway = AgentStubGateway(ArrayDeque(listOf(LlmGatewayResponse(content = "wrapped result"))))
        val agent = BaseAsyncLlmAgent(
            broker = LlmBroker(gateway),
            model = "test",
            behaviour = "be helpful",
        )
        val tool = ToolWrapper(agent, "helper", "Defers to the helper sub-agent.")

        val result = tool.execute(buildJsonObject { put("input", JsonPrimitive("do the thing")) })

        assertEquals("wrapped result", result)
        val passed = gateway.completeCalls.first()
        assertEquals("do the thing", passed.last().content)
    }

    @Test
    fun descriptorExposesInputParameter() {
        val agent = BaseAsyncLlmAgent(broker = LlmBroker(AgentStubGateway(ArrayDeque())), model = "test")
        val tool = ToolWrapper(agent, "helper", "Help me")

        val descriptor = tool.descriptor
        val props = descriptor.parameters["properties"]!!.jsonObject
        assertTrue("input" in props)
        assertEquals("helper", descriptor.name)
        assertEquals("Help me", descriptor.description)
    }

    @Test
    fun missingInputRaises() = runTest {
        val agent = BaseAsyncLlmAgent(broker = LlmBroker(AgentStubGateway(ArrayDeque())), model = "test")
        val tool = ToolWrapper(agent, "helper", "Help")

        assertFailsWith<IllegalStateException> {
            tool.execute(buildJsonObject { })
        }
    }
}
