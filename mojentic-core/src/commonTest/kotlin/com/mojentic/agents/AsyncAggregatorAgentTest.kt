package com.mojentic.agents

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class AggAlphaEvent : Event()
private class AggBetaEvent : Event()

private class CollectingAggregator : AsyncAggregatorAgent(listOf(AggAlphaEvent::class, AggBetaEvent::class)) {
    val processed: MutableList<List<Event>> = mutableListOf()
    override suspend fun processEvents(events: List<Event>): List<Event> {
        processed += events
        return emptyList()
    }
}

class AsyncAggregatorAgentTest {
    @Test
    fun waitsUntilAllRequiredEventTypesArrive() = runTest {
        val agent = CollectingAggregator()
        val alpha = AggAlphaEvent().apply { correlationId = "c-1" }
        val beta = AggBetaEvent().apply { correlationId = "c-1" }

        assertEquals(emptyList<Event>(), agent.receiveEvent(alpha))
        assertTrue(agent.processed.isEmpty())

        agent.receiveEvent(beta)

        assertEquals(1, agent.processed.size)
        assertEquals(setOf(alpha, beta), agent.processed.first().toSet())
    }

    @Test
    fun bucketsByCorrelationId() = runTest {
        val agent = CollectingAggregator()
        val alphaA = AggAlphaEvent().apply { correlationId = "a" }
        val betaB = AggBetaEvent().apply { correlationId = "b" }

        agent.receiveEvent(alphaA)
        agent.receiveEvent(betaB)

        assertTrue(agent.processed.isEmpty())
    }

    @Test
    fun ignoresEventsWithoutCorrelationId() = runTest {
        val agent = CollectingAggregator()
        agent.receiveEvent(AggAlphaEvent())
        assertTrue(agent.processed.isEmpty())
    }
}
