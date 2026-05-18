package com.mojentic.examples

import com.mojentic.agents.BaseAsyncLlmAgent
import com.mojentic.agents.ToolWrapper
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.tools.DateResolverTool
import com.mojentic.llm.tools.files.OkioFilesystemGateway
import com.mojentic.llm.tools.files.fileToolsFor
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Demonstrates wrapping specialist agents as `LlmTool`s via [ToolWrapper].
 *
 * Two specialists — a temporal agent and a sandbox-scoped knowledge agent —
 * are exposed to a coordinator agent. The coordinator delegates date work to
 * the temporal specialist and file work to the knowledge specialist.
 */
fun main(): Unit = runBlocking {
    val sandboxRoot = System.getenv("MOJENTIC_SANDBOX")
        ?: (System.getProperty("java.io.tmpdir").toPath() / "mojentic-coding-tool-demo").toString()
    FileSystem.SYSTEM.createDirectories(sandboxRoot.toPath())
    println("Sandbox: $sandboxRoot")

    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val broker = LlmBroker(gateway)
        val files = OkioFilesystemGateway(FileSystem.SYSTEM, sandboxRoot)

        val temporalSpecialist = BaseAsyncLlmAgent(
            broker = broker,
            model = model,
            behaviour = "You are a temporal specialist: resolve dates and times using the available tools.",
            tools = listOf(DateResolverTool()),
        )

        val knowledgeSpecialist = BaseAsyncLlmAgent(
            broker = broker,
            model = model,
            behaviour = "You manage knowledge stored as files in a sandbox. " +
                "Use list, read, write, and create_directory tools as needed.",
            tools = fileToolsFor(files),
        )

        val coordinator = BaseAsyncLlmAgent(
            broker = broker,
            model = model,
            behaviour = "You coordinate two specialists. Delegate work via the tools available.",
            tools = listOf(
                ToolWrapper(
                    temporalSpecialist,
                    "temporal_specialist",
                    "A specialist who resolves dates and times.",
                ),
                ToolWrapper(
                    knowledgeSpecialist,
                    "knowledge_specialist",
                    "A specialist who can list, read, write, and organise files in the sandbox.",
                ),
            ),
        )

        val plan = """
            I need to plan two tasks:
            - On Monday, call the bank.
            - On Wednesday, drive into the city for work.

            For each day, create a markdown file named "YYYY-MM-DD-ToDo.md" using the resolved date,
            and include the list of tasks for that day.
        """.trimIndent()

        val result = coordinator.generateResponse(plan)
        println("\n--- coordinator response ---\n${result.content}")
    } finally {
        gateway.close()
    }
}
