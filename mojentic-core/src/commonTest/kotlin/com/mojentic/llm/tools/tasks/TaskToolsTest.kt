package com.mojentic.llm.tools.tasks

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun parse(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

class TaskToolsTest {
    @Test
    fun appendTaskToolEmitsTaskJson() = runTest {
        val list = EphemeralTaskList()
        val tool = AppendTaskTool(list)

        val result = tool.execute(
            buildJsonObject { put("description", JsonPrimitive("write tests")) },
        )

        val payload = parse(result)
        assertEquals(1, payload["id"]!!.jsonPrimitive.content.toInt())
        assertEquals("write tests", payload["description"]!!.jsonPrimitive.content)
        assertEquals("pending", payload["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun startThenCompleteFlowsThroughStateMachine() = runTest {
        val list = EphemeralTaskList()
        val task = list.append("work")

        val startResult = parse(
            StartTaskTool(list).execute(
                buildJsonObject { put("id", JsonPrimitive(task.id)) },
            ),
        )
        val completeResult = parse(
            CompleteTaskTool(list).execute(
                buildJsonObject { put("id", JsonPrimitive(task.id)) },
            ),
        )

        assertEquals("in_progress", startResult["status"]!!.jsonPrimitive.content)
        assertEquals("completed", completeResult["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun listTasksToolReturnsArrayAndCount() = runTest {
        val list = EphemeralTaskList()
        list.append("a")
        list.append("b")

        val result = parse(ListTasksTool(list).execute(JsonObject(emptyMap())))

        assertEquals(2, result["count"]!!.jsonPrimitive.content.toInt())
        assertEquals(2, result["tasks"]!!.jsonArray.size)
    }

    @Test
    fun clearTasksToolReportsClearedCount() = runTest {
        val list = EphemeralTaskList()
        list.append("a")
        list.append("b")

        val result = parse(ClearTasksTool(list).execute(JsonObject(emptyMap())))

        assertEquals(2, result["cleared"]!!.jsonPrimitive.content.toInt())
        assertTrue(list.list().isEmpty())
    }

    @Test
    fun missingArgumentRaisesIllegalState() = runTest {
        val list = EphemeralTaskList()
        val tool = AppendTaskTool(list)

        assertFailsWith<IllegalStateException> {
            tool.execute(JsonObject(emptyMap()))
        }
    }

    @Test
    fun taskToolsForReturnsAllSevenTools() {
        val list = EphemeralTaskList()

        val tools = taskToolsFor(list)

        val names = tools.map { it.name }.toSet()
        assertEquals(
            setOf(
                "append_task",
                "prepend_task",
                "insert_task_after",
                "start_task",
                "complete_task",
                "list_tasks",
                "clear_tasks",
            ),
            names,
        )
    }
}
