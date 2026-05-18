package com.mojentic.examples

import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
data class WeatherReport(
    val city: String,
    val temperatureCelsius: Int,
    val condition: String,
)

/**
 * Demonstrates structured output via [LlmBroker.completeJson]. The JSON schema
 * is derived from `WeatherReport` automatically.
 */
fun main() = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val broker = LlmBroker(gateway)
        val report: WeatherReport = broker.completeJson(
            model = model,
            messages = listOf(
                LlmMessage.system(
                    "You report imaginary weather. Always respond with a JSON object matching the schema.",
                ),
                LlmMessage.user("Report the weather in Toronto today."),
            ),
        )
        println("$report")
    } finally {
        gateway.close()
    }
}
