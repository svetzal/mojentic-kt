package com.mojentic.agents

import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmGatewayResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReActAgentTest {
    @Test
    fun stopsImmediatelyWhenLlmEmitsFinalAnswer() = runTest {
        val gateway = AgentStubGateway(
            ArrayDeque(
                listOf(
                    LlmGatewayResponse(content = "Thought: easy.\nFINAL ANSWER: 42"),
                ),
            ),
        )
        val agent = ReActAgent(broker = LlmBroker(gateway), model = "test")

        val answer = agent.solve("what is the meaning of life?")

        assertEquals("42", answer)
        assertEquals(1, agent.steps().size)
        assertTrue(agent.steps().single().final)
    }

    @Test
    fun keepsLoopingUntilFinalAnswerArrives() = runTest {
        val gateway = AgentStubGateway(
            ArrayDeque(
                listOf(
                    LlmGatewayResponse(content = "Thought: I need to look this up."),
                    LlmGatewayResponse(content = "Thought: still working."),
                    LlmGatewayResponse(content = "FINAL ANSWER: Ottawa"),
                ),
            ),
        )
        val agent = ReActAgent(
            broker = LlmBroker(gateway),
            model = "test",
            maxIterations = 5,
        )

        val answer = agent.solve("capital of Canada?")

        assertEquals("Ottawa", answer)
        assertEquals(3, agent.steps().size)
        assertEquals(listOf(false, false, true), agent.steps().map { it.final })
    }

    @Test
    fun returnsLastContentWhenIterationBudgetIsExhausted() = runTest {
        val gateway = AgentStubGateway(
            ArrayDeque(
                listOf(
                    LlmGatewayResponse(content = "Thought: turn 1"),
                    LlmGatewayResponse(content = "Thought: turn 2"),
                ),
            ),
        )
        val agent = ReActAgent(
            broker = LlmBroker(gateway),
            model = "test",
            maxIterations = 2,
        )

        val answer = agent.solve("unsolvable")

        assertEquals("Thought: turn 2", answer)
        assertEquals(2, agent.steps().size)
        assertTrue(agent.steps().none { it.final })
    }
}
