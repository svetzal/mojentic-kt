package com.mojentic.llm.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private val DESCRIPTOR = ToolDescriptor(
    name = "ask_user",
    description =
    "If you do not know how to proceed, ask the user a question, or ask them for " +
        "help or to do something for you.",
    parameters = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put(
            "properties",
            buildJsonObject {
                put(
                    "user_request",
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put(
                            "description",
                            JsonPrimitive(
                                "The question you need the user to answer, or the " +
                                    "task you need the user to do for you.",
                            ),
                        )
                    },
                )
            },
        )
        put("required", JsonArray(listOf(JsonPrimitive("user_request"))))
    },
)

/**
 * Ask the user a question and return their answer to the LLM.
 *
 * The tool delegates the I/O to [gateway] so callers can plug in any
 * UI channel (console, mobile prompt, test stub).
 */
public class AskUserTool(private val gateway: UserInteractionGateway) : LlmTool {
    override val descriptor: ToolDescriptor = DESCRIPTOR

    override suspend fun execute(arguments: JsonObject): String {
        val request = (arguments["user_request"] as? JsonPrimitive)?.content
            ?: error("ask_user: missing 'user_request' argument")
        val answer = gateway.ask(request)
        return buildJsonObject {
            put("user_response", JsonPrimitive(answer))
        }.toString()
    }
}
