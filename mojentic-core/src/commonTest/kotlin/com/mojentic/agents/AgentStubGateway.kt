package com.mojentic.agents

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmGateway
import com.mojentic.llm.LlmGatewayResponse
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.tools.LlmTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonObject

internal class AgentStubGateway(
    private val replies: ArrayDeque<LlmGatewayResponse>,
) : LlmGateway {
    val completeCalls: MutableList<List<LlmMessage>> = mutableListOf()
    val completeTools: MutableList<List<LlmTool>?> = mutableListOf()

    override suspend fun complete(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
    ): LlmGatewayResponse {
        completeCalls += messages
        completeTools += tools
        return replies.removeFirst()
    }

    override suspend fun completeJson(
        model: String,
        messages: List<LlmMessage>,
        schema: JsonObject,
        config: CompletionConfig,
    ): JsonObject = JsonObject(emptyMap())

    override fun stream(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
    ): Flow<GatewayStreamEvent> = emptyFlow()

    override suspend fun availableModels(): List<String> = emptyList()
}
