package com.mojentic.websearch.serpapi

import com.mojentic.errors.WebSearchGatewayException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SerpApiWebSearchGatewayTest {
    private lateinit var gateway: SerpApiWebSearchGateway

    @AfterTest
    fun tearDown() {
        if (::gateway.isInitialized) gateway.close()
    }

    @Test
    fun searchParsesOrganicResults() = runTest {
        val engine = MockEngine { request ->
            val params = request.url.parameters
            assertEquals("test-key", params["api_key"])
            assertEquals("google", params["engine"])
            assertEquals("kotlin multiplatform", params["q"])
            respond(
                content = """
                    {"organic_results":[
                        {"title":"KMP Home","link":"https://kotl.in/kmp","snippet":"Multiplatform overview"},
                        {"title":"KMP Guide","link":"https://kotl.in/guide","snippet":"Getting started"}
                    ]}
                """.trimIndent().replace("\n", "").replace("  ", ""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        gateway = SerpApiWebSearchGateway(apiKey = "test-key", engine = engine)

        val results = gateway.search("kotlin multiplatform")

        assertEquals(2, results.size)
        assertEquals("KMP Home", results[0].title)
        assertEquals("https://kotl.in/kmp", results[0].link)
        assertEquals("Multiplatform overview", results[0].snippet)
    }

    @Test
    fun searchPassesLocale() = runTest {
        val engine = MockEngine { request ->
            assertEquals("fr", request.url.parameters["hl"])
            respond(
                content = """{"organic_results":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        gateway = SerpApiWebSearchGateway(apiKey = "test-key", engine = engine)

        val results = gateway.search("croissant", locale = "fr")

        assertTrue(results.isEmpty())
    }

    @Test
    fun searchHandlesMissingResults() = runTest {
        val engine = MockEngine {
            respond(
                content = """{}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        gateway = SerpApiWebSearchGateway(apiKey = "test-key", engine = engine)

        val results = gateway.search("anything")

        assertTrue(results.isEmpty())
    }

    @Test
    fun nonOkResponseRaisesException() = runTest {
        val engine = MockEngine {
            respond(
                content = "unauthorised",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }
        gateway = SerpApiWebSearchGateway(apiKey = "bad-key", engine = engine)

        assertFailsWith<WebSearchGatewayException> {
            gateway.search("anything")
        }
    }
}
