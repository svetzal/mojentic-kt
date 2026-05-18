package com.mojentic.tracer

import com.mojentic.llm.LlmMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class TracerSystemTest {
    @Test
    fun recordLlmCallStoresEvent() = runTest {
        val tracer = TracerSystem()

        tracer.recordLlmCall(
            model = "gpt",
            messages = listOf(LlmMessage.user("hi")),
            temperature = 0.5,
            tools = listOf("calc"),
            correlationId = "abc",
        )

        val events = tracer.eventStore.getEvents()
        val event = events.single() as LlmCallEvent
        assertEquals("gpt", event.model)
        assertEquals(1, event.messages.size)
        assertEquals(0.5, event.temperature)
        assertEquals(listOf("calc"), event.tools)
        assertEquals("abc", event.correlationId)
    }

    @Test
    fun recordToolBatchStoresBatchEvent() = runTest {
        val tracer = TracerSystem()

        tracer.recordToolBatch(
            batchId = "batch-1",
            toolNames = listOf("a", "b"),
            successCount = 2,
            failureCount = 0,
            callDuration = 12.milliseconds,
            correlationId = "cid",
            caller = "broker",
        )

        val event = tracer.eventStore.getEvents().single() as ToolBatchEvent
        assertEquals("batch-1", event.batchId)
        assertEquals(listOf("a", "b"), event.toolNames)
        assertEquals(2, event.successCount)
        assertEquals(0, event.failureCount)
        assertEquals("broker", event.caller)
    }

    @Test
    fun disableSkipsRecording() = runTest {
        val tracer = TracerSystem()
        tracer.disable()

        tracer.recordLlmCall(
            model = "m",
            messages = emptyList(),
            temperature = 1.0,
            tools = null,
            correlationId = "cid",
        )

        assertEquals(0, tracer.eventStore.size())
        assertFalse(tracer.enabled)
    }

    @Test
    fun reEnableResumesRecording() = runTest {
        val tracer = TracerSystem(enabled = false)
        tracer.enable()

        tracer.recordLlmCall(
            model = "m",
            messages = emptyList(),
            temperature = 1.0,
            tools = null,
            correlationId = "cid",
        )

        assertEquals(1, tracer.eventStore.size())
        assertTrue(tracer.enabled)
    }

    @Test
    fun clearEmptiesUnderlyingStore() = runTest {
        val tracer = TracerSystem()
        tracer.recordLlmCall(
            model = "m",
            messages = emptyList(),
            temperature = 1.0,
            tools = null,
            correlationId = "cid",
        )

        tracer.clear()

        assertEquals(0, tracer.eventStore.size())
    }

    @Test
    fun printableSummaryIncludesEventKey() = runTest {
        val tracer = TracerSystem()
        tracer.recordLlmCall(
            model = "model-x",
            messages = listOf(LlmMessage.user("hi"), LlmMessage.user("again")),
            temperature = 0.7,
            tools = listOf("t1"),
            correlationId = "cid-99",
        )

        val event = tracer.eventStore.getEvents().single() as LlmCallEvent
        val summary = event.printableSummary()
        assertTrue(summary.contains("LlmCallEvent"))
        assertTrue(summary.contains("cid-99"))
        assertTrue(summary.contains("model-x"))
        assertTrue(summary.contains("t1"))
    }
}
