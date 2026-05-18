package com.mojentic.llm.tools

import com.mojentic.llm.LlmToolCall
import com.mojentic.tracer.ToolBatchEvent
import com.mojentic.tracer.TracerSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class StubTool(private val toolName: String, private val body: suspend (JsonObject) -> String) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = toolName,
        description = "stub",
        parameters = JsonObject(emptyMap()),
    )

    override suspend fun execute(arguments: JsonObject): String = body(arguments)
}

private fun call(name: String, value: String = "v"): LlmToolCall = LlmToolCall(
    id = "id-$name",
    name = name,
    arguments = JsonObject(mapOf("k" to JsonPrimitive(value))),
)

class ParallelToolRunnerTest {
    @Test
    fun runBatchExecutesEveryKnownTool() = runTest {
        val tools = listOf(
            StubTool("a") { """{"r":"a"}""" },
            StubTool("b") { """{"r":"b"}""" },
        )
        val runner = ParallelToolRunner()

        val outcomes = runner.runBatch(listOf(call("a"), call("b")), tools)

        assertEquals(setOf("a", "b"), outcomes.map { it.call.name }.toSet())
        assertTrue(outcomes.all { it.isOk })
    }

    @Test
    fun runBatchSkipsUnknownTools() = runTest {
        val tools = listOf(StubTool("a") { """{"r":"a"}""" })
        val runner = ParallelToolRunner()

        val outcomes = runner.runBatch(listOf(call("a"), call("missing")), tools)

        assertEquals(listOf("a"), outcomes.map { it.call.name })
    }

    @Test
    fun runBatchCapturesPerCallFailures() = runTest {
        val tools = listOf(
            StubTool("ok") { """{"r":"ok"}""" },
            StubTool("boom") { throw RuntimeException("nope") },
        )
        val runner = ParallelToolRunner()

        val outcomes = runner.runBatch(listOf(call("ok"), call("boom")), tools)

        val byName = outcomes.associateBy { it.call.name }
        assertTrue(byName.getValue("ok").isOk)
        assertFalse(byName.getValue("boom").isOk)
    }

    @Test
    fun runBatchEmitsBatchEventWithCorrelationId() = runTest {
        val tracer = TracerSystem()
        val tools = listOf(
            StubTool("a") { """{"r":"a"}""" },
            StubTool("b") { """{"r":"b"}""" },
        )
        val runner = ParallelToolRunner(tracer = tracer, caller = "test")

        runner.runBatch(listOf(call("a"), call("b")), tools, correlationId = "cid-42")

        val batch = tracer.eventStore.getEvents(type = ToolBatchEvent::class).single() as ToolBatchEvent
        assertEquals("cid-42", batch.correlationId)
        assertEquals(2, batch.successCount)
        assertEquals(0, batch.failureCount)
        assertEquals("test", batch.caller)
        assertEquals(listOf("a", "b").sorted(), batch.toolNames.sorted())
    }

    @Test
    fun runBatchRunsConcurrently() = runTest {
        val tools = listOf(
            StubTool("slow1") {
                delay(50)
                """{"r":"slow1"}"""
            },
            StubTool("slow2") {
                delay(50)
                """{"r":"slow2"}"""
            },
        )
        val runner = ParallelToolRunner()

        val outcomes = runner.runBatch(listOf(call("slow1"), call("slow2")), tools)

        // runTest auto-advances virtual time; both should still complete OK.
        assertEquals(2, outcomes.size)
        assertTrue(outcomes.all { it.isOk })
    }
}
