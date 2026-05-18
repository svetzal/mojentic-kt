package com.mojentic.examples

import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.tools.CurrentDateTimeTool
import com.mojentic.llm.tools.ParallelToolRunner
import com.mojentic.ollama.OllamaGateway
import com.mojentic.tracer.LlmCallEvent
import com.mojentic.tracer.LlmResponseEvent
import com.mojentic.tracer.ToolBatchEvent
import com.mojentic.tracer.ToolCallEvent
import com.mojentic.tracer.TracerSystem
import kotlinx.coroutines.runBlocking

/**
 * Wires a [TracerSystem] into [LlmBroker] alongside a [ParallelToolRunner] and
 * prints every recorded event after the run completes.
 *
 * Run with a local Ollama instance and any tool-capable model
 * (`MOJENTIC_MODEL`, default `qwen2.5:7b`).
 */
fun main(): Unit = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val tracer = TracerSystem()
    val gateway = OllamaGateway()
    try {
        val broker = LlmBroker(
            gateway = gateway,
            tracer = tracer,
            toolRunner = ParallelToolRunner(tracer = tracer, caller = "tracer-demo"),
        )

        broker.complete(
            model = model,
            messages = listOf(
                LlmMessage.system("Use the provided tool to answer. Be concise."),
                LlmMessage.user("What time is it right now?"),
            ),
            tools = listOf(CurrentDateTimeTool()),
        ).also { response -> println("\nFinal answer: ${response.content}\n") }

        println("--- recorded events ---")
        tracer.eventStore.getEvents().forEach { event ->
            when (event) {
                is LlmCallEvent -> println(event.printableSummary())
                is LlmResponseEvent -> println(event.printableSummary())
                is ToolCallEvent -> println(event.printableSummary())
                is ToolBatchEvent -> println(event.printableSummary())
                else -> println(event)
            }
            println()
        }
    } finally {
        gateway.close()
    }
}
