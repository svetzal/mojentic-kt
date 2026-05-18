package com.mojentic.llm.tools

import kotlinx.serialization.json.JsonObject

/**
 * JSON-Schema description of a tool exposed to the LLM.
 *
 * @property name Tool name as the LLM will address it.
 * @property description Human-readable summary for the LLM to decide when to call it.
 * @property parameters JSON Schema describing the expected arguments object.
 */
public data class ToolDescriptor(val name: String, val description: String, val parameters: JsonObject)

/**
 * A tool the LLM may call during a completion.
 *
 * Implementations are `suspend` so I/O-bound tools can integrate cleanly with
 * structured concurrency. Cancellation of the surrounding coroutine cancels
 * the tool execution cooperatively — implementations should honour
 * `ensureActive()` in long-running loops.
 */
public interface LlmTool {
    public val descriptor: ToolDescriptor

    public val name: String
        get() = descriptor.name

    public val description: String
        get() = descriptor.description

    public fun matches(toolName: String): Boolean = toolName == name

    public suspend fun execute(arguments: JsonObject): String
}
