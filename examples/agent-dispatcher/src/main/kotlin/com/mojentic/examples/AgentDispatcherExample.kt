package com.mojentic.examples

import com.mojentic.agents.Agent
import com.mojentic.agents.AsyncDispatcher
import com.mojentic.agents.BaseAsyncLlmAgent
import com.mojentic.agents.Event
import com.mojentic.agents.Router
import com.mojentic.agents.TerminateEvent
import com.mojentic.agents.ToolWrapper
import com.mojentic.llm.LlmBroker
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking

/**
 * Demonstrates wiring a Router + AsyncDispatcher with two agents:
 *
 * - A "translator" sub-agent wrapped as an `LlmTool` via [ToolWrapper].
 * - A "coordinator" agent that receives a [UserQuery], asks the LLM (which
 *   can delegate to the translator tool), and emits a [Response] that
 *   terminates the dispatcher.
 */
private class UserQuery(val text: String) : Event()
private class Response(val text: String) : TerminateEvent()

private class CoordinatorAgent(private val agent: BaseAsyncLlmAgent) : Agent {
    override suspend fun receiveEvent(event: Event): List<Event> {
        val query = event as UserQuery
        val response = agent.generateResponse(query.text)
        println("\n--- coordinator response ---\n${response.content}")
        return listOf(Response(response.content.orEmpty()))
    }
}

fun main(): Unit = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val broker = LlmBroker(gateway)

        val translator = BaseAsyncLlmAgent(
            broker = broker,
            model = model,
            behaviour = "You translate the given English text into French. Reply with only the translation.",
        )
        val translateTool = ToolWrapper(translator, "translate_to_french", "Translates English text into French.")

        val coordinator = BaseAsyncLlmAgent(
            broker = broker,
            model = model,
            behaviour = "You are an assistant. When asked for a translation, call the translate_to_french tool.",
            tools = listOf(translateTool),
        )

        val router = Router()
        router.addRoute(UserQuery::class, CoordinatorAgent(coordinator))
        val dispatcher = AsyncDispatcher(router)
        dispatcher.start(this)
        dispatcher.dispatch(UserQuery("Translate 'good morning, world' into French."))
        dispatcher.waitForEmptyQueue(timeoutMs = 60_000)
        dispatcher.stop()
    } finally {
        gateway.close()
    }
}
