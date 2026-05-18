package com.mojentic.llm.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private val DESCRIPTOR = ToolDescriptor(
    name = "tell_user",
    description =
    "Display a message to the user without expecting a response. Use this to send " +
        "important intermediate information to the user as you work on completing their request.",
    parameters = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put(
            "properties",
            buildJsonObject {
                put(
                    "message",
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put(
                            "description",
                            JsonPrimitive("The important message you want to display to the user."),
                        )
                    },
                )
            },
        )
        put("required", JsonArray(listOf(JsonPrimitive("message"))))
    },
)

/**
 * Display a message to the user without waiting for a reply.
 *
 * Returns a small acknowledgement JSON object so the LLM can continue
 * reasoning after the tool call.
 */
public class TellUserTool(private val gateway: UserInteractionGateway) : LlmTool {
    override val descriptor: ToolDescriptor = DESCRIPTOR

    override suspend fun execute(arguments: JsonObject): String {
        val message = (arguments["message"] as? JsonPrimitive)?.content
            ?: error("tell_user: missing 'message' argument")
        gateway.tell(message)
        return """{"status":"delivered"}"""
    }
}
