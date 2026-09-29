package com.mojentic.examples

import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.omlx.OmlxGateway
import kotlinx.coroutines.runBlocking

/**
 * Runs one turn against a local oMLX server and prints the answer, the
 * model's reasoning and the evidence the server reported.
 *
 * Reads:
 *  - `OMLX_HOST` (defaults to `http://localhost:8000`; the gateway adds `/v1`).
 *  - `OMLX_API_KEY` (optional; sent as a bearer token when set).
 *  - `OMLX_MODEL` (defaults to the first model the server lists).
 */
fun main(): Unit = runBlocking {
    val gateway = OmlxGateway()
    try {
        val model = System.getenv("OMLX_MODEL")
            ?: gateway.availableModels().firstOrNull()
            ?: error("The oMLX server lists no models. Set OMLX_MODEL or add a model in the oMLX dashboard.")
        println("model: $model")

        val response = LlmBroker(gateway).complete(
            model = model,
            messages = listOf(
                LlmMessage.system("You are a concise assistant. Reply in one sentence."),
                LlmMessage.user("Give me one fun fact about octopuses."),
            ),
        )

        response.thinking?.let { println("[thinking]\n$it\n") }
        println(response.content ?: "[no content]")
        println("\nfinish reason: ${response.finishReason}")
        println("usage: ${response.usage}")
        if (response.finishReason != "stop") println("The reply is incomplete. Content is not an answer unless the finish reason is stop.")
    } finally {
        gateway.close()
    }
}
