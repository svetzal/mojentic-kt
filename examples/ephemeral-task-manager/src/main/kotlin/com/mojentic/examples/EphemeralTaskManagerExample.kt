package com.mojentic.examples

import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.tools.tasks.EphemeralTaskList
import com.mojentic.llm.tools.tasks.taskToolsFor
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking

/**
 * Lets the LLM plan a multi-step task by maintaining its own task list
 * through the seven [com.mojentic.llm.tools.tasks] tools. The list lives
 * in this process and is discarded when the example exits.
 */
fun main(): Unit = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val tasks = EphemeralTaskList()
        val broker = LlmBroker(gateway)

        broker.complete(
            model = model,
            messages = listOf(
                LlmMessage.system(
                    "You plan work by maintaining a task list using the provided tools. " +
                        "First append every step you intend to take, then start and complete " +
                        "each task in order. Reply with a summary of what you did.",
                ),
                LlmMessage.user("Plan and complete a quick coffee-making process."),
            ),
            tools = taskToolsFor(tasks),
        ).also { response -> println("\n--- assistant ---\n${response.content}") }

        println("\n--- final task list (${tasks.list().size}) ---")
        tasks.list().forEach { task -> println("  ${task.id}. [${task.status.wireValue}] ${task.description}") }
    } finally {
        gateway.close()
    }
}
