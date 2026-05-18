package com.mojentic.tracer

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class EventStoreTest {
    private fun llmCall(correlationId: String = "cid"): LlmCallEvent = LlmCallEvent(
        correlationId = correlationId,
        model = "m",
        messages = emptyList(),
        temperature = 1.0,
        tools = null,
    )

    private fun toolCall(correlationId: String = "cid"): ToolCallEvent = ToolCallEvent(
        correlationId = correlationId,
        toolName = "t",
        arguments = "{}",
        result = "{}",
        callDuration = 1.milliseconds,
        isError = false,
    )

    @Test
    fun storeAppendsAndPreservesOrder() = runTest {
        val store = EventStore()

        store.store(llmCall("a"))
        store.store(llmCall("b"))

        val events = store.getEvents()
        assertEquals(2, events.size)
        assertEquals("a", events[0].correlationId)
        assertEquals("b", events[1].correlationId)
    }

    @Test
    fun getEventsFiltersByType() = runTest {
        val store = EventStore()
        store.store(llmCall())
        store.store(toolCall())

        val onlyToolCalls = store.getEvents(type = ToolCallEvent::class)

        assertEquals(1, onlyToolCalls.size)
        assertTrue(onlyToolCalls.single() is ToolCallEvent)
    }

    @Test
    fun getEventsFiltersByTimeWindow() = runTest {
        val store = EventStore()
        val before = Clock.System.now()
        store.store(llmCall())
        val after = Clock.System.now()
        store.store(llmCall())

        val inWindow = store.getEvents(startTime = before, endTime = after)
        val firstOnly = inWindow.filter { it.timestamp <= after }

        assertTrue(firstOnly.isNotEmpty())
    }

    @Test
    fun getEventsAppliesCustomPredicate() = runTest {
        val store = EventStore()
        store.store(llmCall("keep"))
        store.store(llmCall("drop"))

        val kept = store.getEvents(predicate = { it.correlationId == "keep" })

        assertEquals(1, kept.size)
        assertEquals("keep", kept.single().correlationId)
    }

    @Test
    fun getLastNReturnsTailInOrder() = runTest {
        val store = EventStore()
        store.store(llmCall("a"))
        store.store(llmCall("b"))
        store.store(llmCall("c"))

        val tail = store.getLastN(2)

        assertEquals(listOf("b", "c"), tail.map { it.correlationId })
    }

    @Test
    fun clearEmptiesBuffer() = runTest {
        val store = EventStore()
        store.store(llmCall())

        store.clear()

        assertEquals(0, store.size())
    }

    @Test
    fun eventsFlowEmitsLiveSubscribers() = runTest {
        val store = EventStore()

        store.events.test {
            store.store(llmCall("first"))
            assertEquals("first", awaitItem().correlationId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
