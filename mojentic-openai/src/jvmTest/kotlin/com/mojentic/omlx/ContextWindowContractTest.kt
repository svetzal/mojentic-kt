package com.mojentic.omlx

import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmGateway
import com.mojentic.llm.LlmGatewayResponse
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.ModelContextWindow
import com.mojentic.llm.tools.LlmTool
import com.mojentic.ollama.OllamaGateway
import com.mojentic.ollama.effectiveContextWindow
import com.mojentic.openai.OpenAIGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Executes the unchanged Elixir context-window-v1 artifact through public gateways and real HTTP. */
class ContextWindowContractTest {
    private val artifact = File("../contracts/context_window_v1.json").readBytes()
    private val contract = Json.parseToJsonElement(artifact.decodeToString()).jsonObject

    @Test
    fun artifactIdentity() {
        assertEquals("context-window-v1", contract.getValue("version").jsonPrimitive.content)
        assertEquals(
            "9327502962112a020bafb46c6a677366df732f5a29cd44c92f17e8f632281395",
            MessageDigest.getInstance("SHA-256").digest(artifact).joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun sharedHttpCases() = runTest {
        for (element in contract.getValue("cases").jsonArray) {
            val row = element.jsonObject
            val provider = row.text("provider")
            val label = "$provider/${row.text("name")}"
            val reply = ScriptedReply(row.getValue("status").jsonPrimitive.int, row.text("response"))
            val key = if (row["without_api_key"]?.jsonPrimitive?.content == "true") "" else "contract-key"
            RecoveryScriptedServer(listOf(reply)).use { server ->
                val gateway = if (provider == "ollama") OllamaGateway(server.url) else OmlxGateway(server.url, apiKey = key)
                try {
                    val expected = row.getValue("expected").jsonObject
                    if ("error" in expected) {
                        val failure = assertFailsWith<LlmGatewayException>(label) { gateway.modelContextWindow(row.text("model")) }
                        val error = expected.getValue("error")
                        if (error is JsonArray) assertTrue(failure.message.orEmpty().contains(error[1].toString()), label)
                    } else {
                        assertEquals(window(expected["window"]), gateway.modelContextWindow(row.text("model")), label)
                    }
                    assertRequest(server, row, key, label)
                } finally {
                    close(gateway)
                }
            }
        }
    }

    @Test
    fun registryLookupHasAnActiveZeroRequestObserverAndNegativeControl() = runTest {
        RecoveryScriptedServer(emptyList()).use { server ->
            val gateway = OpenAIGateway("contract-key", "${server.url}/v1")
            try {
                for (element in contract.getValue("registry_cases").jsonArray) {
                    val row = element.jsonObject
                    assertEquals(window(row["window"]), gateway.modelContextWindow(row.text("model")), row.text("model"))
                    assertTrue(server.requests.isEmpty())
                }
                // The same live observer must detect actual I/O and reject an unplanned request.
                assertFailsWith<LlmGatewayException> { gateway.availableModels() }
                assertEquals(listOf("GET /v1/models HTTP/1.1"), server.requestLines)
                assertEquals("Bearer contract-key", server.requestHeaders.single()["authorization"])
            } finally {
                gateway.close()
            }
        }
    }

    @Test
    fun sharedEffectiveCasesAndUnchangedConfigurationDefaults() {
        for (element in contract.getValue("effective_cases").jsonArray) {
            val row = element.jsonObject
            assertEquals(
                row["expected"]?.jsonPrimitive?.intOrNull,
                effectiveContextWindow(window(row["window"]), row["num_ctx"]?.jsonPrimitive?.intOrNull),
                row.toString(),
            )
        }
        val capacity = ModelContextWindow(262144, "fixture", 65536)
        assertEquals(32768, CompletionConfig().numCtx)
        assertEquals(32768, effectiveContextWindow(capacity, CompletionConfig()))
        assertEquals(65536, effectiveContextWindow(capacity))
        assertFailsWith<IllegalArgumentException> { effectiveContextWindow(capacity, 0) }
    }

    @Test
    fun legacyGatewayInheritsNotAdvertised() = runTest {
        assertNull(LegacyGateway().modelContextWindow("legacy"))
    }

    @Test
    fun transportFailuresRetainTypedCause() = runTest {
        for (provider in listOf("ollama", "omlx")) {
            RecoveryScriptedServer(List(4) { ScriptedReply(0, "") }).use { server ->
                val gateway = if (provider == "ollama") OllamaGateway(server.url) else OmlxGateway(server.url, apiKey = "")
                try {
                    val failure = assertFailsWith<LlmGatewayException> { gateway.modelContextWindow("exact-model") }
                    assertNotNull(failure.cause)
                    assertTrue(server.requestLines.isNotEmpty())
                    assertTrue(
                        server.requestLines.all {
                            it ==
                                if (provider == "ollama") "POST /api/show HTTP/1.1" else "GET /v1/models HTTP/1.1"
                        },
                    )
                } finally {
                    close(gateway)
                }
            }
        }
    }

    @Test
    fun additionalMalformedShapesMissingModelsAndOptionalFields() = runTest {
        val invalidOmlx = listOf(
            "{}",
            "{\"data\":null}",
            "{\"data\":{}}",
            "{\"data\":[null]}",
            "{\"data\":[{}]}",
            "{\"data\":[{\"id\":42}]}",
        ) + listOf("null", "0", "-1", "1.5", "true", "\"8192\"", "2147483648").map {
            "{\"data\":[{\"id\":\"exact-model\",\"max_model_len\":$it}]}"
        }
        val missingOmlx = listOf(
            "{\"data\":[]}",
            "{\"data\":[{\"id\":\"exact-model-extra\",\"max_model_len\":1}]}",
            "{\"data\":[{\"id\":\"exact-model\"}]}",
        )
        for (body in invalidOmlx + missingOmlx) {
            RecoveryScriptedServer(listOf(ScriptedReply(200, body))).use { server ->
                val gateway = OmlxGateway(server.url, apiKey = "")
                try {
                    if (body in invalidOmlx) {
                        assertFailsWith<LlmGatewayException>(body) { gateway.modelContextWindow("exact-model") }
                    } else {
                        assertNull(gateway.modelContextWindow("exact-model"), body)
                    }
                } finally {
                    gateway.close()
                }
            }
        }
        val invalidOllama = listOf(
            "{\"model_info\":[]}",
            "{\"model_info\":42}",
            "{\"parameters\":42}",
            "{\"model_info\":{\"arch.context_length\":2147483648}}",
        )
        for (body in invalidOllama) {
            RecoveryScriptedServer(listOf(ScriptedReply(200, body))).use { server ->
                val gateway = OllamaGateway(server.url)
                try {
                    assertFailsWith<LlmGatewayException>(body) { gateway.modelContextWindow("exact-model") }
                } finally {
                    gateway.close()
                }
            }
        }
        val body = """{"model_info":{"z.context_length":999,"a.context_length":8192},
            "parameters":"temperature 0.6\n  num_ctx\t4096\n", "future_field":true}"""
        RecoveryScriptedServer(listOf(ScriptedReply(200, body))).use { server ->
            val gateway = OllamaGateway(server.url)
            try {
                assertEquals(ModelContextWindow(8192, "model_info.a.context_length", 4096), gateway.modelContextWindow("exact-model"))
            } finally {
                gateway.close()
            }
        }
    }

    @Test
    fun ancillaryModelListCasesCharacterizeExistingBoundaryDifferences() = runTest {
        for (element in contract.getValue("lookup_cases").jsonArray) {
            val row = element.jsonObject
            val provider = row.text("provider")
            RecoveryScriptedServer(listOf(ScriptedReply(200, row.getValue("response").toString()))).use { server ->
                val gateway = when (provider) {
                    "ollama" -> OllamaGateway(server.url)
                    "omlx" -> OmlxGateway(server.url, apiKey = "contract-key")
                    else -> OpenAIGateway("contract-key", "${server.url}/v1")
                }
                try {
                    if (provider == "ollama") {
                        // Existing Kotlin tags parsing requires `model`; this ancillary row supplies only `name`.
                        // Retain and report the pre-existing gap rather than change model listing in this correction.
                        assertFailsWith<LlmGatewayException> { gateway.availableModels() }
                    } else {
                        assertEquals(row.getValue("names").jsonArray.map { it.jsonPrimitive.content }, gateway.availableModels())
                    }
                    assertEquals(listOf("GET ${row.text("path")} HTTP/1.1"), server.requestLines)
                    assertContentEquals(byteArrayOf(), server.requests.single())
                } finally {
                    when (gateway) {
                        is OpenAIGateway -> gateway.close()
                        else -> close(gateway)
                    }
                }
            }
        }
    }

    @Test
    fun redirectsAreHttpErrorsWithoutChangingCompletionClients() = runTest {
        for (provider in listOf("ollama", "omlx")) {
            val replies = listOf(ScriptedReply(302, "", mapOf("Location" to "/sentinel")), ScriptedReply(200, "{}"))
            RecoveryScriptedServer(replies).use { server ->
                val gateway = if (provider == "ollama") OllamaGateway(server.url) else OmlxGateway(server.url, apiKey = "")
                try {
                    val failure = assertFailsWith<LlmGatewayException> { gateway.modelContextWindow("exact-model") }
                    assertTrue(failure.message.orEmpty().contains("302"))
                    val path = if (provider == "ollama") "POST /api/show" else "GET /v1/models"
                    assertEquals(listOf("$path HTTP/1.1"), server.requestLines)
                } finally {
                    close(gateway)
                }
            }
        }
    }

    private fun assertRequest(server: RecoveryScriptedServer, row: JsonObject, key: String, label: String) {
        val request = row.getValue("request").jsonObject
        assertEquals(listOf("${request.text("method")} ${request.text("path")} HTTP/1.1"), server.requestLines, label)
        val body = request["body"]
        if (body == JsonNull) {
            assertContentEquals(byteArrayOf(), server.requests.single(), label)
        } else {
            assertEquals(body, Json.parseToJsonElement(server.requests.single().decodeToString()), label)
            assertEquals("application/json", server.requestHeaders.single()["content-type"], label)
        }
        val auth = if (row.text("provider") == "omlx" && key.isNotEmpty()) "Bearer $key" else null
        assertEquals(auth, server.requestHeaders.single()["authorization"], label)
    }

    private fun window(value: JsonElement?): ModelContextWindow? = if (value == null || value == JsonNull) {
        null
    } else {
        value.jsonObject.let {
            ModelContextWindow(
                it.getValue("max_context_tokens").jsonPrimitive.int,
                it.text("field"),
                it["model_num_ctx"]?.jsonPrimitive?.int,
            )
        }
    }

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

    private fun close(gateway: LlmGateway) {
        when (gateway) {
            is OllamaGateway -> gateway.close()
            is OmlxGateway -> gateway.close()
        }
    }

    private class LegacyGateway : LlmGateway {
        override suspend fun complete(model: String, messages: List<LlmMessage>, tools: List<LlmTool>?, config: CompletionConfig) =
            LlmGatewayResponse(content = "legacy")

        override suspend fun completeJson(model: String, messages: List<LlmMessage>, schema: JsonObject, config: CompletionConfig) = schema

        override fun stream(
            model: String,
            messages: List<LlmMessage>,
            tools: List<LlmTool>?,
            config: CompletionConfig,
        ): Flow<GatewayStreamEvent> = emptyFlow()

        override suspend fun availableModels(): List<String> = emptyList()
    }
}
