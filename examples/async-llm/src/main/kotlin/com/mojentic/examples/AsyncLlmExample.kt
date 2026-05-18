package com.mojentic.examples

import com.mojentic.agents.AsyncAggregatorAgent
import com.mojentic.agents.AsyncDispatcher
import com.mojentic.agents.BaseAsyncLlmAgent
import com.mojentic.agents.Event
import com.mojentic.agents.Router
import com.mojentic.agents.TerminateEvent
import com.mojentic.llm.LlmBroker
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking

/**
 * Demonstrates two [BaseAsyncLlmAgent]s fanning out from a single question
 * event and an [AsyncAggregatorAgent] joining their results into one final
 * answer event. All wiring is event-driven through an [AsyncDispatcher].
 */
private class QuestionEvent(val question: String) : Event()
private class FactsEvent(val question: String, val facts: String) : Event()
private class AnswerEvent(val question: String, val answer: String) : Event()
private class FinalEvent(val text: String) : TerminateEvent()

private class FactCheckerAgent(private val agent: BaseAsyncLlmAgent) : com.mojentic.agents.Agent {
    override suspend fun receiveEvent(event: Event): List<Event> {
        val q = event as QuestionEvent
        val response = agent.generateResponse("List 2-3 short facts relevant to: ${q.question}")
        return listOf(
            FactsEvent(q.question, response.content.orEmpty()).also { it.correlationId = event.correlationId },
        )
    }
}

private class AnswerGeneratorAgent(private val agent: BaseAsyncLlmAgent) : com.mojentic.agents.Agent {
    override suspend fun receiveEvent(event: Event): List<Event> {
        val q = event as QuestionEvent
        val response = agent.generateResponse("Answer concisely: ${q.question}")
        return listOf(
            AnswerEvent(q.question, response.content.orEmpty()).also { it.correlationId = event.correlationId },
        )
    }
}

private class FinalAnswerAggregator :
    AsyncAggregatorAgent(listOf(FactsEvent::class, AnswerEvent::class)) {
    override suspend fun processEvents(events: List<Event>): List<Event> {
        val facts = events.filterIsInstance<FactsEvent>().single()
        val answer = events.filterIsInstance<AnswerEvent>().single()
        val combined = """
            |Question: ${facts.question}
            |Answer:   ${answer.answer.trim()}
            |Facts:
            |${facts.facts.trim()}
        """.trimMargin()
        return listOf(FinalEvent(combined))
    }
}

private class PrinterAgent : com.mojentic.agents.Agent {
    override suspend fun receiveEvent(event: Event): List<Event> {
        println("\n--- final ---\n${(event as FinalEvent).text}")
        return emptyList()
    }
}

fun main(): Unit = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val broker = LlmBroker(gateway)
        val factChecker = FactCheckerAgent(
            BaseAsyncLlmAgent(broker, model, behaviour = "You provide concise factual statements."),
        )
        val answerGenerator = AnswerGeneratorAgent(
            BaseAsyncLlmAgent(broker, model, behaviour = "You answer questions concisely."),
        )
        val aggregator = FinalAnswerAggregator()
        val printer = PrinterAgent()

        val router = Router()
        router.addRoute(QuestionEvent::class, factChecker)
        router.addRoute(QuestionEvent::class, answerGenerator)
        router.addRoute(FactsEvent::class, aggregator)
        router.addRoute(AnswerEvent::class, aggregator)
        router.addRoute(FinalEvent::class, printer)

        val dispatcher = AsyncDispatcher(router)
        dispatcher.start(this)
        dispatcher.dispatch(QuestionEvent("What is the capital of France?"))
        dispatcher.waitForEmptyQueue(timeoutMs = 60_000)
        dispatcher.stop()
    } finally {
        gateway.close()
    }
}
