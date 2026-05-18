package com.mojentic.openai

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class OpenAIEmbeddingsGatewayTest {
    private lateinit var gateway: OpenAIEmbeddingsGateway

    @AfterTest
    fun tearDown() {
        if (::gateway.isInitialized) gateway.close()
    }

    @Test
    fun embedBatchPreservesOrderAndDecodesFloats() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                {"data":[
                  {"index":1,"embedding":[0.4,0.5,0.6]},
                  {"index":0,"embedding":[0.1,0.2,0.3]}
                ]}
                """.trimIndent().replace("\n", ""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        gateway = OpenAIEmbeddingsGateway(apiKey = "test", engine = engine)

        val out = gateway.embedBatch("text-embedding-3-small", listOf("a", "b"))

        assertEquals(2, out.size)
        assertContentEquals(floatArrayOf(0.1f, 0.2f, 0.3f), out[0])
        assertContentEquals(floatArrayOf(0.4f, 0.5f, 0.6f), out[1])
    }
}
