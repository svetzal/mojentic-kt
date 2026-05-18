package com.mojentic.examples

import com.mojentic.agents.BaseAsyncLlmAgentWithMemory
import com.mojentic.context.SharedWorkingMemory
import com.mojentic.llm.LlmBroker
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Demonstrates a [BaseAsyncLlmAgentWithMemory] backed by a
 * [SharedWorkingMemory]. The memory is seeded with a small "User" record;
 * the agent injects the memory snapshot into every prompt so the LLM can use
 * remembered facts when answering.
 */
fun main(): Unit = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val memory = SharedWorkingMemory(
            mapOf(
                "user" to buildJsonObject {
                    put("name", JsonPrimitive("Stacey"))
                    put("age", JsonPrimitive(56))
                },
            ),
        )

        val agent = BaseAsyncLlmAgentWithMemory(
            broker = LlmBroker(gateway),
            model = model,
            memory = memory,
            behaviour = "You are a helpful assistant who pays attention to things you have already been told.",
            instructions = "Answer the user's question using what you know and what you remember.",
        )

        val response = agent.generateResponse(
            "What is my name, and how old am I? Also: I just adopted a dog named Boomer.",
        )
        println("\n--- assistant ---\n${response.content}")

        agent.mergeMemory(
            mapOf<String, JsonElement>(
                "pets" to buildJsonObject {
                    put("dog", JsonPrimitive("Boomer"))
                },
            ),
        )

        val followUp = agent.generateResponse("Remind me — do I have any pets?")
        println("\n--- assistant (after memory merge) ---\n${followUp.content}")
    } finally {
        gateway.close()
    }
}
