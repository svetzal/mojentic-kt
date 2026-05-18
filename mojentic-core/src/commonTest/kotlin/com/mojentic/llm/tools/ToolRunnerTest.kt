package com.mojentic.llm.tools

import com.mojentic.llm.LlmToolCall
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class EchoTool : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "echo",
        description = "Echoes the input",
        parameters = buildJsonObject { put("type", JsonPrimitive("object")) },
    )

    override suspend fun execute(arguments: JsonObject): String = arguments.toString()
}

private class FailingTool : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "boom",
        description = "Always fails",
        parameters = buildJsonObject { put("type", JsonPrimitive("object")) },
    )

    override suspend fun execute(arguments: JsonObject): String = throw IllegalStateException("nope")
}

class ToolRunnerTest {
    @Test
    fun serialRunnerExecutesKnownCallsInOrder() = runTest {
        val runner = SerialToolRunner()
        val tools = listOf(EchoTool())
        val calls = listOf(
            LlmToolCall(name = "echo", arguments = buildJsonObject { put("a", JsonPrimitive(1)) }),
            LlmToolCall(name = "echo", arguments = buildJsonObject { put("a", JsonPrimitive(2)) }),
        )

        val outcomes = runner.runBatch(calls, tools)

        assertEquals(2, outcomes.size)
        assertTrue(outcomes.all { it.isOk })
        assertEquals("""{"a":1}""", outcomes[0].result)
        assertEquals("""{"a":2}""", outcomes[1].result)
    }

    @Test
    fun unmatchedCallsAreSkipped() = runTest {
        val runner = SerialToolRunner()
        val outcomes = runner.runBatch(
            listOf(LlmToolCall(name = "unknown", arguments = JsonObject(emptyMap()))),
            listOf(EchoTool()),
        )

        assertTrue(outcomes.isEmpty())
    }

    @Test
    fun failingToolRecordsErrorAndContinues() = runTest {
        val runner = SerialToolRunner()
        val outcomes = runner.runBatch(
            listOf(
                LlmToolCall(name = "boom", arguments = JsonObject(emptyMap())),
                LlmToolCall(name = "echo", arguments = buildJsonObject { put("x", JsonPrimitive("y")) }),
            ),
            listOf(EchoTool(), FailingTool()),
        )

        assertEquals(2, outcomes.size)
        assertFalse(outcomes[0].isOk)
        assertNotNull(outcomes[0].error)
        assertNull(outcomes[0].result)
        assertTrue(outcomes[1].isOk)
        assertEquals("""{"x":"y"}""", outcomes[1].result)
    }
}
