package com.mojentic.examples

import com.mojentic.agents.IterativeProblemSolver
import com.mojentic.llm.ChatSession
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.tools.DateResolverTool
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Wraps an [IterativeProblemSolver] as an `LlmTool` and hands it to a
 * top-level [ChatSession]. The chat-level model dispatches multi-step
 * problems to the solver while keeping a simple Q&A surface for the user.
 */
private class IterativeProblemSolverTool(
    private val broker: LlmBroker,
    private val model: String,
    private val tools: List<LlmTool>,
) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "iterative_problem_solver",
        description = "Iteratively solve a complex multi-step problem using the available tools.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put(
                "properties",
                buildJsonObject {
                    put(
                        "problem_to_solve",
                        buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The problem or request to be solved."))
                        },
                    )
                },
            )
            put("required", buildJsonArray { add(JsonPrimitive("problem_to_solve")) })
            put("additionalProperties", JsonPrimitive(false))
        },
    )

    override suspend fun execute(arguments: JsonObject): String {
        val problem = arguments["problem_to_solve"]?.jsonPrimitive?.content
            ?: error("iterative_problem_solver requires 'problem_to_solve'")
        val solver = IterativeProblemSolver(
            broker = broker,
            model = model,
            availableTools = tools,
            maxIterations = 3,
        )
        return solver.solve(problem)
    }
}

fun main(): Unit = runBlocking {
    val model = System.getenv("MOJENTIC_MODEL") ?: "qwen2.5:7b"
    val gateway = OllamaGateway()
    try {
        val broker = LlmBroker(gateway)
        val chat = ChatSession(
            broker = broker,
            model = model,
            systemPrompt = "You are a helpful assistant. Delegate multi-step problems to the iterative_problem_solver tool.",
            tools = listOf(IterativeProblemSolverTool(broker, model, listOf(DateResolverTool()))),
        )

        val query = "What date is next Friday? Give the answer in YYYY-MM-DD."
        println("Query: $query")
        val response = chat.send(query)
        println("\n--- assistant ---\n${response.content}")
    } finally {
        gateway.close()
    }
}
