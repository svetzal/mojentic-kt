package com.mojentic.llm.tools.websearch

import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

private val DESCRIPTOR = ToolDescriptor(
    name = "organic_web_search",
    description = "Search the Internet for information matching the given query and return the organic search " +
        "results (title, link, snippet).",
    parameters = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put(
            "properties",
            buildJsonObject {
                put(
                    "query",
                    buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("The search query."))
                    },
                )
            },
        )
        put("required", JsonArray(listOf(JsonPrimitive("query"))))
    },
)

/**
 * LLM-facing tool that delegates to a [WebSearchGateway].
 *
 * Returns a JSON array of `{ title, link, snippet }` objects ready to be
 * appended to the assistant context.
 */
public class OrganicWebSearchTool(private val gateway: WebSearchGateway) : LlmTool {
    override val descriptor: ToolDescriptor = DESCRIPTOR

    override suspend fun execute(arguments: JsonObject): String {
        val query = (arguments["query"] as? JsonPrimitive)?.content
            ?: error("organic_web_search: missing 'query' argument")
        val results = gateway.search(query)
        return buildJsonArray {
            results.forEach { result ->
                add(
                    buildJsonObject {
                        put("title", JsonPrimitive(result.title))
                        put("link", JsonPrimitive(result.link))
                        put("snippet", JsonPrimitive(result.snippet))
                    },
                )
            }
        }.toString()
    }
}
