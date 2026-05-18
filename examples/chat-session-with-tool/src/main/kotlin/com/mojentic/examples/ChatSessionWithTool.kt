package com.mojentic.examples

import com.mojentic.llm.ChatSession
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.tools.CurrentDateTimeTool
import com.mojentic.llm.tools.DateResolverTool
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking

/**
 * A chat session with date-related tools attached. The LLM can call
 * `current_datetime` and `resolve_date` to answer time-aware questions.
 */
fun main(): Unit = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val session = ChatSession(
            broker = LlmBroker(gateway),
            model = model,
            systemPrompt = """
                You are a scheduling assistant. When the user asks about dates or
                times, use the supplied tools — do not guess.
            """.trimIndent(),
            tools = listOf(CurrentDateTimeTool(), DateResolverTool()),
        )

        val turns = listOf(
            "What time is it right now?",
            "And what date is next Friday?",
            "How many days from now is that?",
        )
        for (turn in turns) {
            println("you> $turn")
            val response = session.send(turn)
            println("ai > ${response.content}\n")
        }
    } finally {
        gateway.close()
    }
}
