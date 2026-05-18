package com.mojentic.agents

import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmGatewayResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IterativeProblemSolverTest {
    @Test
    fun stopsImmediatelyWhenLlmReturnsDone() = runTest {
        val gateway = AgentStubGateway(
            ArrayDeque(
                listOf(
                    LlmGatewayResponse(content = "DONE"),
                    LlmGatewayResponse(content = "Final answer is 42."),
                ),
            ),
        )
        val solver = IterativeProblemSolver(broker = LlmBroker(gateway), model = "test")

        val result = solver.solve("what is the meaning of life?")

        assertEquals("Final answer is 42.", result)
        assertEquals(2, gateway.completeCalls.size)
    }

    @Test
    fun stopsAtMaxIterations() = runTest {
        val gateway = AgentStubGateway(
            ArrayDeque(
                listOf(
                    LlmGatewayResponse(content = "thinking..."),
                    LlmGatewayResponse(content = "still thinking"),
                    LlmGatewayResponse(content = "summary"),
                ),
            ),
        )
        val solver = IterativeProblemSolver(
            broker = LlmBroker(gateway),
            model = "test",
            maxIterations = 2,
        )

        val result = solver.solve("hard problem")

        assertEquals("summary", result)
        assertEquals(3, gateway.completeCalls.size)
    }

    @Test
    fun stopsOnFail() = runTest {
        val gateway = AgentStubGateway(
            ArrayDeque(
                listOf(
                    LlmGatewayResponse(content = "FAIL"),
                    LlmGatewayResponse(content = "I could not solve it."),
                ),
            ),
        )
        val solver = IterativeProblemSolver(broker = LlmBroker(gateway), model = "test")

        val result = solver.solve("impossible")

        assertEquals("I could not solve it.", result)
    }
}
