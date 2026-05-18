package com.mojentic.agents

import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Adapts a [BaseAsyncLlmAgent] so it can be passed as a tool to another LLM.
 *
 * The wrapped agent exposes a single string parameter `input` — the calling
 * LLM passes natural-language instructions, which become a user message to the
 * inner agent. The agent's textual response is returned to the outer LLM.
 *
 * Mirrors the Python reference's `ToolWrapper`.
 */
public class ToolWrapper(
    private val agent: BaseAsyncLlmAgent,
    private val toolName: String,
    private val toolDescription: String,
) : LlmTool {

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = toolName,
        description = toolDescription,
        parameters = buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "input",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Instructions for this agent.")
                        },
                    )
                },
            )
            put("required", buildJsonArray { add("input") })
            put("additionalProperties", false)
        },
    )

    override suspend fun execute(arguments: JsonObject): String {
        val input = arguments["input"]?.jsonPrimitive?.content
            ?: error("ToolWrapper '$toolName' requires an 'input' argument.")
        val response = agent.generateResponse(input)
        return response.content.orEmpty()
    }
}
