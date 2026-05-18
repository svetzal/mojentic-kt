package com.mojentic.examples

import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.tools.websearch.OrganicWebSearchTool
import com.mojentic.ollama.OllamaGateway
import com.mojentic.websearch.serpapi.SerpApiWebSearchGateway
import kotlinx.coroutines.runBlocking

/**
 * Wires the [OrganicWebSearchTool] (via SerpApi) into a broker so the LLM
 * can answer with up-to-date information from the web.
 *
 * Requires `SERPAPI_API_KEY` in the environment. Defaults to the `qwen2.5:7b`
 * model on a local Ollama server; override with `MOJENTIC_MODEL`.
 */
fun main(): Unit = runBlocking {
    val apiKey = System.getenv("SERPAPI_API_KEY")
        ?: error("Set SERPAPI_API_KEY in the environment to run this example.")
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"

    val llmGateway = OllamaGateway()
    val searchGateway = SerpApiWebSearchGateway(apiKey = apiKey)
    try {
        val broker = LlmBroker(llmGateway)
        val response = broker.complete(
            model = model,
            messages = listOf(
                LlmMessage.system(
                    "You have a `organic_web_search` tool for fresh information. " +
                        "Search whenever you need facts you don't reliably know.",
                ),
                LlmMessage.user("Who won the most recent Formula 1 World Drivers' Championship?"),
            ),
            tools = listOf(OrganicWebSearchTool(searchGateway)),
        )
        println("\n--- assistant ---\n${response.content}")
    } finally {
        searchGateway.close()
        llmGateway.close()
    }
}
