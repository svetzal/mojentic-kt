package com.mojentic.omlx

import com.mojentic.llm.LlmMessage
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Scripted peers are derived from the versioned executable contract artifact. */
internal object NativeReasoningCases {
    val artifact = File("../contracts/native_reasoning_v1.json").readBytes()
    val contract = Json.parseToJsonElement(artifact.decodeToString()).jsonObject
    val providers = listOf(RecoveryTestFixtures.Provider.OLLAMA, RecoveryTestFixtures.Provider.OMLX)
    val messages = listOf(LlmMessage.user("question"))
    val schema = Json.parseToJsonElement("""{"type":"object","properties":{"answer":{"type":"string"}}}""").jsonObject

    fun reply(provider: RecoveryTestFixtures.Provider, name: String): ScriptedReply = ScriptedReply(
        200,
        contract.getValue("providers").jsonObject.getValue(provider.name.lowercase()).jsonObject.getValue(name).jsonPrimitive.content,
    )

    fun incompleteStream(provider: RecoveryTestFixtures.Provider): String {
        val body = reply(provider, "stream_tool").body
        if (provider == RecoveryTestFixtures.Provider.OLLAMA) {
            val frame = Json.parseToJsonElement(body.trim()).jsonObject
            return JsonObject(frame - "done_reason" + ("done" to JsonPrimitive(false))).toString() + "\n"
        }
        val frame = Json.parseToJsonElement(body.lineSequence().first().removePrefix("data: ")).jsonObject
        val choice = frame.getValue("choices").jsonArray.single().jsonObject
        return "data: " + JsonObject(mapOf("choices" to JsonArray(listOf(JsonObject(choice - "finish_reason"))))) + "\n\n"
    }

    fun unfinishedOrdinary(provider: RecoveryTestFixtures.Provider): ScriptedReply {
        val original = reply(provider, "ordinary_tool")
        val frame = Json.parseToJsonElement(original.body).jsonObject
        val changed = if (provider == RecoveryTestFixtures.Provider.OLLAMA) {
            JsonObject(frame + ("done" to JsonPrimitive(false)))
        } else {
            val choice = frame.getValue("choices").jsonArray.single().jsonObject
            JsonObject(frame + ("choices" to JsonArray(listOf(JsonObject(choice + ("finish_reason" to JsonPrimitive("length")))))))
        }
        return original.copy(body = changed.toString())
    }

    fun requests(server: RecoveryScriptedTransport): List<JsonObject> = server.requests.map {
        Json.parseToJsonElement(it.decodeToString()).jsonObject
    }

    fun messages(request: JsonObject): List<JsonObject> = request.getValue("messages").jsonArray.map { it.jsonObject }

    fun tool(executed: MutableList<String>): LlmTool = object : LlmTool {
        override val descriptor = ToolDescriptor("lookup", "Lookup", schema)
        override suspend fun execute(arguments: JsonObject): String {
            val key = arguments.getValue("key").jsonPrimitive.content
            executed += key
            return "tool-result-$key"
        }
    }
}
