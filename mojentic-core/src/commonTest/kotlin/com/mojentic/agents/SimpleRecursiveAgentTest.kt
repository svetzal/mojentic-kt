package com.mojentic.agents

import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmGatewayResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimpleRecursiveAgentTest {
    @Test
    fun returnsSolutionWhenLlmSaysDone() = runTest {
        val gateway = AgentStubGateway(ArrayDeque(listOf(LlmGatewayResponse(content = "DONE"))))
        val agent = SimpleRecursiveAgent(broker = LlmBroker(gateway), model = "test")

        val solution = agent.solve("solvable")

        assertEquals("DONE", solution)
        val events = agent.history()
        assertTrue(events.first() is SolverEvent.GoalSubmitted)
        assertTrue(events.any { it is SolverEvent.GoalAchieved })
    }

    @Test
    fun returnsFailureWhenLlmSaysFail() = runTest {
        val gateway = AgentStubGateway(ArrayDeque(listOf(LlmGatewayResponse(content = "FAIL"))))
        val agent = SimpleRecursiveAgent(broker = LlmBroker(gateway), model = "test")

        val solution = agent.solve("impossible")

        assertTrue(solution.startsWith("Failed to solve after 1 iterations"))
        assertTrue(agent.history().any { it is SolverEvent.GoalFailed })
    }

    @Test
    fun returnsBestSolutionWhenMaxIterationsReached() = runTest {
        val gateway = AgentStubGateway(
            ArrayDeque(
                listOf(
                    LlmGatewayResponse(content = "in progress"),
                    LlmGatewayResponse(content = "still working"),
                ),
            ),
        )
        val agent = SimpleRecursiveAgent(
            broker = LlmBroker(gateway),
            model = "test",
            maxIterations = 2,
        )

        val solution = agent.solve("hard problem")

        assertTrue(solution.startsWith("Best solution after 2 iterations"))
        assertTrue(solution.endsWith("still working"))
    }
}
