package com.mojentic.llm.tools.websearch

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class StubWebSearchGateway(private val results: List<WebSearchResult>) : WebSearchGateway {
    var lastQuery: String? = null
    override suspend fun search(query: String, locale: String?): List<WebSearchResult> {
        lastQuery = query
        return results
    }
}

class OrganicWebSearchToolTest {
    @Test
    fun executePassesQueryAndSerialisesResults() = runTest {
        val gateway = StubWebSearchGateway(
            listOf(
                WebSearchResult("A", "https://a.example", "snip-a"),
                WebSearchResult("B", "https://b.example", "snip-b"),
            ),
        )
        val tool = OrganicWebSearchTool(gateway)

        val result = tool.execute(
            buildJsonObject { put("query", JsonPrimitive("kotlin")) },
        )

        assertEquals("kotlin", gateway.lastQuery)
        val parsed = Json.parseToJsonElement(result) as JsonArray
        assertEquals(2, parsed.size)
        val first = parsed[0] as JsonObject
        assertEquals("A", (first["title"] as JsonPrimitive).content)
        assertEquals("https://a.example", (first["link"] as JsonPrimitive).content)
        assertEquals("snip-a", (first["snippet"] as JsonPrimitive).content)
    }

    @Test
    fun missingQueryThrows() = runTest {
        val tool = OrganicWebSearchTool(StubWebSearchGateway(emptyList()))

        assertFailsWith<IllegalStateException> {
            tool.execute(JsonObject(emptyMap()))
        }
    }
}
