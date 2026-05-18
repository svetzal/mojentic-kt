package com.mojentic.examples

import com.mojentic.llm.ChatSession
import com.mojentic.llm.LlmBroker
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking

/**
 * A multi-turn chat session that owns its own message history.
 *
 * Each turn only sends the user's new line; the session remembers everything
 * else. Reads from STDIN until EOF (Ctrl+D).
 */
fun main(): Unit = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val session = ChatSession(
            broker = LlmBroker(gateway),
            model = model,
            systemPrompt = "You are a terse but friendly assistant. Reply in one or two sentences.",
        )

        println("Chat with $model — Ctrl+D to exit.")
        while (true) {
            print("you> ")
            val line = readLine() ?: break
            if (line.isBlank()) continue
            val response = session.send(line)
            println("ai > ${response.content}")
        }
        println("\nFinal history (${session.messages().size} messages).")
    } finally {
        gateway.close()
    }
}
