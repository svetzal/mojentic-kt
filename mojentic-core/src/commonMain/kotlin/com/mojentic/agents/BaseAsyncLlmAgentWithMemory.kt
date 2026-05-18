package com.mojentic.agents

import com.mojentic.context.SharedWorkingMemory
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmGatewayResponse
import com.mojentic.llm.LlmMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * LLM-backed agent that remembers facts in a [SharedWorkingMemory].
 *
 * Before each turn the agent injects the current memory contents into the
 * conversation as a system-style hint. After the turn, callers may use the
 * returned response — for the Python reference the memory is updated from a
 * structured-output field; the Kotlin port keeps the merge explicit so callers
 * can pull the relevant block from a structured response themselves and pass
 * it to [mergeMemory] (which is just a convenience wrapper around the memory's
 * own merge).
 */
public open class BaseAsyncLlmAgentWithMemory(
    broker: LlmBroker,
    model: String,
    public val memory: SharedWorkingMemory,
    behaviour: String,
    public val instructions: String,
    config: CompletionConfig = CompletionConfig(),
) : BaseAsyncLlmAgent(
    broker = broker,
    model = model,
    behaviour = behaviour,
    config = config,
) {

    override fun createInitialMessages(): List<LlmMessage> = emptyList()

    private suspend fun buildMessages(content: String): List<LlmMessage> {
        val snapshot = memory.getWorkingMemory()
        val memoryJson = Json.encodeToString(
            JsonObject.serializer(),
            JsonObject(snapshot),
        )
        return listOf(
            LlmMessage.system(behaviour),
            LlmMessage.user(
                "This is what you remember:\n" +
                    memoryJson +
                    "\n\nRemember anything new you learn by adding to your working memory.",
            ),
            LlmMessage.user(instructions),
            LlmMessage.user(content),
        )
    }

    override suspend fun generateResponse(content: String): LlmGatewayResponse {
        val messages = buildMessages(content)
        return broker.complete(
            model = model,
            messages = messages,
            tools = tools,
            config = config,
        )
    }

    public suspend fun mergeMemory(delta: Map<String, kotlinx.serialization.json.JsonElement>) {
        memory.mergeToWorkingMemory(delta)
    }
}
