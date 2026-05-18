package com.mojentic.agents

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class InputEvent(val payload: String) : Event()
private class WorkEvent(val payload: String) : Event()
private class DoneEvent(val payload: String) : Event()

private class FanOutAgent : Agent {
    override suspend fun receiveEvent(event: Event): List<Event> {
        val input = event as InputEvent
        return listOf(WorkEvent(input.payload), DoneEvent(input.payload))
    }
}

private class TerminatingAgent : Agent {
    val seen: MutableList<Event> = mutableListOf()
    override suspend fun receiveEvent(event: Event): List<Event> {
        seen += event
        return if (event is DoneEvent) listOf(TerminateEvent()) else emptyList()
    }
}

private class RecordingAgent : Agent {
    val seen: MutableList<Event> = mutableListOf()
    override suspend fun receiveEvent(event: Event): List<Event> {
        seen += event
        return emptyList()
    }
}

class AsyncDispatcherTest {
    @Test
    fun assignsCorrelationIdWhenMissing() = runTest {
        val router = Router()
        val recorder = RecordingAgent()
        router.addRoute(InputEvent::class, recorder)
        val dispatcher = AsyncDispatcher(router, pollDelayMs = 1)
        dispatcher.start(this)

        val event = InputEvent("hello")
        dispatcher.dispatch(event)
        assertTrue(dispatcher.waitForEmptyQueue(timeoutMs = 1_000))
        dispatcher.stop()

        assertNotNull(event.correlationId)
        assertEquals(1, recorder.seen.size)
    }

    @Test
    fun routesProducedEventsBackToTheirAgents() = runTest {
        val router = Router()
        val recorder = RecordingAgent()
        router.addRoute(InputEvent::class, FanOutAgent())
        router.addRoute(WorkEvent::class, recorder)
        router.addRoute(DoneEvent::class, recorder)
        val dispatcher = AsyncDispatcher(router, pollDelayMs = 1)
        dispatcher.start(this)

        dispatcher.dispatch(InputEvent("payload"))
        assertTrue(dispatcher.waitForEmptyQueue(timeoutMs = 1_000))
        dispatcher.stop()

        assertEquals(2, recorder.seen.size)
        assertTrue(recorder.seen.any { it is WorkEvent })
        assertTrue(recorder.seen.any { it is DoneEvent })
    }

    @Test
    fun terminateEventStopsTheLoop() = runTest {
        val router = Router()
        val terminator = TerminatingAgent()
        router.addRoute(InputEvent::class, FanOutAgent())
        router.addRoute(DoneEvent::class, terminator)
        val dispatcher = AsyncDispatcher(router, pollDelayMs = 1)
        val job = dispatcher.start(this)

        dispatcher.dispatch(InputEvent("payload"))
        assertTrue(dispatcher.waitForEmptyQueue(timeoutMs = 1_000))
        dispatcher.stop()

        assertTrue(job.isCompleted || job.isCancelled)
    }

    @Test
    fun unrelatedEventTypeRoutesToNoAgent() = runTest {
        val router = Router()
        val recorder = RecordingAgent()
        router.addRoute(InputEvent::class, recorder)
        val dispatcher = AsyncDispatcher(router, pollDelayMs = 1)
        dispatcher.start(this)

        dispatcher.dispatch(WorkEvent("unwanted"))
        assertTrue(dispatcher.waitForEmptyQueue(timeoutMs = 1_000))
        dispatcher.stop()

        assertTrue(recorder.seen.isEmpty())
    }
}
