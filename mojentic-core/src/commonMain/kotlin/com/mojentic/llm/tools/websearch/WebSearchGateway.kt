package com.mojentic.llm.tools.websearch

/**
 * A single organic web-search result.
 */
public data class WebSearchResult(
    val title: String,
    val link: String,
    val snippet: String,
)

/**
 * Pluggable web-search backend. Concrete implementations live in their own
 * modules (e.g. `mojentic-websearch-serpapi`) so callers can pick a vendor
 * without dragging in extra dependencies.
 */
public interface WebSearchGateway {
    /**
     * Run a search and return the organic (non-ad, non-knowledge-panel) results.
     *
     * @param query the user-facing search query.
     * @param locale optional BCP-47 locale hint (e.g. `"en"`); ignored if the
     *   backend doesn't support it.
     */
    public suspend fun search(query: String, locale: String? = null): List<WebSearchResult>
}
