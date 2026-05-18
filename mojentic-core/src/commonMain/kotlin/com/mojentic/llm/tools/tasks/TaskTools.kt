package com.mojentic.llm.tools.tasks

import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

private val JSON = Json { encodeDefaults = true }

private fun stringField(name: String, description: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("object"))
    put(
        "properties",
        buildJsonObject {
            put(
                name,
                buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive(description))
                },
            )
        },
    )
    put("required", JsonArray(listOf(JsonPrimitive(name))))
}

private fun intField(name: String, description: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("object"))
    put(
        "properties",
        buildJsonObject {
            put(
                name,
                buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("description", JsonPrimitive(description))
                },
            )
        },
    )
    put("required", JsonArray(listOf(JsonPrimitive(name))))
}

private fun emptyParams(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("object"))
    put("properties", JsonObject(emptyMap()))
    put("required", JsonArray(emptyList()))
}

private fun requireString(args: JsonObject, key: String, tool: String): String =
    (args[key] as? JsonPrimitive)?.content
        ?: error("$tool: missing '$key' argument")

private fun requireInt(args: JsonObject, key: String, tool: String): Int =
    (args[key] as? JsonPrimitive)?.content?.toIntOrNull()
        ?: error("$tool: '$key' must be an integer")

private fun taskJson(task: EphemeralTask): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(task.id))
    put("description", JsonPrimitive(task.description))
    put("status", JsonPrimitive(task.status.wireValue))
}

/** Append a new pending task to the tail of the list. */
public class AppendTaskTool(private val list: EphemeralTaskList) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "append_task",
        description = "Add a new task to the end of the task list.",
        parameters = stringField("description", "The description of the task to add."),
    )

    override suspend fun execute(arguments: JsonObject): String {
        val description = requireString(arguments, "description", name)
        val task = list.append(description)
        return taskJson(task).toString()
    }
}

/** Prepend a new pending task at the head of the list. */
public class PrependTaskTool(private val list: EphemeralTaskList) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "prepend_task",
        description = "Add a new task to the beginning of the task list.",
        parameters = stringField("description", "The description of the task to add."),
    )

    override suspend fun execute(arguments: JsonObject): String {
        val description = requireString(arguments, "description", name)
        val task = list.prepend(description)
        return taskJson(task).toString()
    }
}

/** Insert a new pending task after an existing one identified by id. */
public class InsertTaskAfterTool(private val list: EphemeralTaskList) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "insert_task_after",
        description = "Insert a new task immediately after the task with the given id.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put(
                "properties",
                buildJsonObject {
                    put(
                        "existing_task_id",
                        buildJsonObject {
                            put("type", JsonPrimitive("integer"))
                            put(
                                "description",
                                JsonPrimitive("Id of the existing task after which to insert the new task."),
                            )
                        },
                    )
                    put(
                        "description",
                        buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put(
                                "description",
                                JsonPrimitive("The description of the new task."),
                            )
                        },
                    )
                },
            )
            put(
                "required",
                JsonArray(listOf(JsonPrimitive("existing_task_id"), JsonPrimitive("description"))),
            )
        },
    )

    override suspend fun execute(arguments: JsonObject): String {
        val existingId = requireInt(arguments, "existing_task_id", name)
        val description = requireString(arguments, "description", name)
        val task = list.insertAfter(existingId, description)
        return taskJson(task).toString()
    }
}

/** Start a pending task (transition to IN_PROGRESS). */
public class StartTaskTool(private val list: EphemeralTaskList) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "start_task",
        description = "Start working on the task with the given id (transitions PENDING → IN_PROGRESS).",
        parameters = intField("id", "Id of the task to start."),
    )

    override suspend fun execute(arguments: JsonObject): String {
        val id = requireInt(arguments, "id", name)
        val task = list.start(id)
        return taskJson(task).toString()
    }
}

/** Complete an in-progress task. */
public class CompleteTaskTool(private val list: EphemeralTaskList) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "complete_task",
        description = "Mark the task with the given id complete (transitions IN_PROGRESS → COMPLETED).",
        parameters = intField("id", "Id of the task to complete."),
    )

    override suspend fun execute(arguments: JsonObject): String {
        val id = requireInt(arguments, "id", name)
        val task = list.complete(id)
        return taskJson(task).toString()
    }
}

/** List every task in the list, in order. */
public class ListTasksTool(private val list: EphemeralTaskList) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "list_tasks",
        description = "List every task with its current status.",
        parameters = emptyParams(),
    )

    override suspend fun execute(arguments: JsonObject): String {
        val tasks = list.list()
        val payload = buildJsonObject {
            put(
                "tasks",
                buildJsonArray { tasks.forEach { add(taskJson(it)) } },
            )
            put("count", JsonPrimitive(tasks.size))
        }
        return JSON.encodeToString(JsonObject.serializer(), payload)
    }
}

/** Clear every task from the list. */
public class ClearTasksTool(private val list: EphemeralTaskList) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "clear_tasks",
        description = "Remove every task from the list.",
        parameters = emptyParams(),
    )

    override suspend fun execute(arguments: JsonObject): String {
        val cleared = list.clear()
        return buildJsonObject {
            put("cleared", JsonPrimitive(cleared))
        }.toString()
    }
}

/**
 * Convenience factory: every task tool wired to one [list].
 */
public fun taskToolsFor(list: EphemeralTaskList): List<LlmTool> = listOf(
    AppendTaskTool(list),
    PrependTaskTool(list),
    InsertTaskAfterTool(list),
    StartTaskTool(list),
    CompleteTaskTool(list),
    ListTasksTool(list),
    ClearTasksTool(list),
)
