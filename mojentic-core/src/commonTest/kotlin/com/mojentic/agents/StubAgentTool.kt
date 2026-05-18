package com.mojentic.agents

import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class StubAgentTool(
    name: String,
    description: String,
    private val response: String,
) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = name,
        description = description,
        parameters = buildJsonObject { put("type", "object") },
    )

    override suspend fun execute(arguments: JsonObject): String = response
}
