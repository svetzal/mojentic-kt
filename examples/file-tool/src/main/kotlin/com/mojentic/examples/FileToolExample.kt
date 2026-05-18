package com.mojentic.examples

import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.tools.files.OkioFilesystemGateway
import com.mojentic.llm.tools.files.fileToolsFor
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Demonstrates the 8 file tools wired to a sandboxed [OkioFilesystemGateway].
 *
 * Seeds a temp directory with a small project, hands the tools to the LLM,
 * and asks it to list and summarise the contents. Override the sandbox root
 * with `MOJENTIC_SANDBOX=/some/path` to point at an existing directory.
 */
fun main(): Unit = runBlocking {
    val sandbox = System.getenv("MOJENTIC_SANDBOX") ?: createDemoSandbox()
    println("Sandbox: $sandbox")

    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val files = OkioFilesystemGateway(FileSystem.SYSTEM, sandbox)
        val broker = LlmBroker(gateway)

        broker.complete(
            model = model,
            messages = listOf(
                LlmMessage.system(
                    "You have file-system tools scoped to a sandbox. Use them to explore the layout and " +
                        "summarise the project for the user.",
                ),
                LlmMessage.user("What's in this project? Give me a one-paragraph summary."),
            ),
            tools = fileToolsFor(files),
        ).also { response -> println("\n--- assistant ---\n${response.content}") }
    } finally {
        gateway.close()
    }
}

private fun createDemoSandbox(): String {
    val tmp = System.getProperty("java.io.tmpdir").toPath() / "mojentic-file-tool-demo"
    val fs = FileSystem.SYSTEM
    fs.createDirectories(tmp)
    fs.write(tmp / "README.md") {
        writeUtf8("# Demo\n\nA tiny sandboxed project to show off the file tools.\n")
    }
    fs.createDirectories(tmp / "src")
    fs.write(tmp / "src" / "main.kt") {
        writeUtf8("fun main() = println(\"hello\")\n")
    }
    return tmp.toString()
}
