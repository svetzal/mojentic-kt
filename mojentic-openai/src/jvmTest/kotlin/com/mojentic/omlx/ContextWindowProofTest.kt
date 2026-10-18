package com.mojentic.omlx

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ContextWindowProofTest {
    @Test
    fun exactSelectionThroughHttp() = runTest {
        val contract = Json.parseToJsonElement(File("../contracts/context_window_v1.json").readText()).jsonObject
        val case = contract.getValue("cases").jsonArray.map { it.jsonObject }.single {
            it.getValue("name").jsonPrimitive.content == "exact-selection-without-api-key"
        }
        val body = case.getValue("response").jsonPrimitive.content
        RecoveryScriptedTransport(listOf(ScriptedReply(200, body))).use { server ->
            val gateway = OmlxGateway(host = server.url, apiKey = "contract-key", engine = server.engine)
            try {
                assertEquals(262144, gateway.modelContextWindow("exact-model")?.maxContextTokens)
                assertEquals(listOf("GET /v1/models HTTP/1.1"), server.requestLines)
            } finally {
                gateway.close()
            }
        }
    }
}
