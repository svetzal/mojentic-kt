package com.mojentic.omlx

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmGateway
import com.mojentic.llm.StreamEventsGateway
import com.mojentic.omlx.AdmissionAmendmentHttpTest.Entry
import com.mojentic.omlx.RecoveryTestFixtures.Provider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextWindowRequestFidelityTest {
    @Test
    fun unsetSamplingAndLimitsAreAbsentThroughEveryEntrypoint() = runTest {
        for (provider in Provider.entries) {
            for (entry in listOf(Entry.ORDINARY, Entry.STRUCTURED, Entry.STRUCTURED_RESPONSE, Entry.STREAM, Entry.EVENTS)) {
                RecoveryScriptedTransport(listOf(entry.success(provider))).use { server ->
                    provider.gateway(server.url, engine = server.engine).use { gateway ->
                        call(gateway.value, entry, CompletionConfig())
                        val body = Json.parseToJsonElement(server.requests.single().decodeToString()).jsonObject
                        val fields = if (provider == Provider.OLLAMA) body.getValue("options").jsonObject else body
                        for (key in listOf("temperature", "num_ctx", "num_predict", "max_tokens", "max_completion_tokens")) {
                            assertTrue(key !in fields, "$provider/$entry unexpectedly sent $key: $body")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun discoveryPreservesOrdinaryStructuredAndBothStreamingRequestBytes() = runTest {
        val contract = Json.parseToJsonElement(File("../contracts/context_window_v1.json").readText()).jsonObject
        val entries = listOf(Entry.ORDINARY, Entry.STRUCTURED, Entry.STRUCTURED_RESPONSE, Entry.STREAM, Entry.EVENTS)
        val configs = listOf(CompletionConfig(), CompletionConfig(temperature = 0.0, numCtx = 8192, maxTokens = 73))
        for (provider in Provider.entries) {
            for (entry in entries) {
                for (config in configs) {
                    val row = contract.getValue("cases").jsonArray.map { it.jsonObject }.firstOrNull {
                        it.getValue("provider").jsonPrimitive.content == provider.name.lowercase()
                    }
                    val discovery = row?.let { ScriptedReply(200, it.getValue("response").jsonPrimitive.content) }
                    val replies = listOfNotNull(entry.success(provider), discovery, entry.success(provider))
                    RecoveryScriptedTransport(replies).use { server ->
                        provider.gateway(server.url, engine = server.engine).use { gateway ->
                            // Characterize completion before lookup on this instance, then compare the exact subsequent bytes.
                            call(gateway.value, entry, config)
                            val baseline = server.requests.single()
                            val body = Json.parseToJsonElement(baseline.decodeToString()).jsonObject
                            assertEquals("test-model", body.getValue("model").jsonPrimitive.content)
                            if (provider == Provider.OLLAMA) {
                                assertEquals(config.numCtx?.toString(), body.getValue("options").jsonObject["num_ctx"]?.toString())
                            }
                            gateway.value.modelContextWindow("exact-model")
                            call(gateway.value, entry, config)
                            assertEquals(replies.size, server.requests.size)
                            assertContentEquals(baseline, server.requests.last(), "$provider/$entry/$config")
                            assertEquals(server.requestLines.first(), server.requestLines.last())
                            assertEquals(server.requestHeaders.first(), server.requestHeaders.last())
                        }
                    }
                }
            }
        }
    }

    private suspend fun call(gateway: LlmGateway, entry: Entry, config: CompletionConfig) {
        val messages = RecoveryTestFixtures.messages
        val schema = RecoveryTestFixtures.schema
        when (entry) {
            Entry.ORDINARY -> assertEquals("ok", gateway.complete("test-model", messages, config = config).content)

            Entry.STRUCTURED -> assertEquals("\"ok\"", gateway.completeJson("test-model", messages, schema, config)["value"].toString())

            Entry.STRUCTURED_RESPONSE -> assertEquals(
                "\"ok\"",
                gateway.completeJsonResponse("test-model", messages, schema, config).structuredJson?.jsonObject?.get("value").toString(),
            )

            Entry.STREAM -> assertEquals(
                "ok",
                gateway.stream("test-model", messages, config = config).toList().filterIsInstance<GatewayStreamEvent.Content>()
                    .joinToString("") { it.text },
            )

            Entry.EVENTS -> {
                val events = (gateway as StreamEventsGateway).streamEvents("test-model", messages, config).toList()
                assertTrue(events.none { it is CompletionStreamEvent.Error })
                assertEquals("ok", events.filterIsInstance<CompletionStreamEvent.Content>().joinToString("") { it.text })
            }

            else -> error("Unsupported fidelity entry $entry")
        }
    }
}
