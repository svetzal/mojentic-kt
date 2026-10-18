package com.mojentic.omlx

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmGateway
import com.mojentic.llm.ReasoningEffort
import com.mojentic.llm.recovery.RecoveryPolicy
import com.mojentic.omlx.RecoveryTestFixtures.Provider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Executes the shared request-fidelity-v1 inputs and responses against in-process HTTP. */
class RequestFidelityContractTest {
    private val bytes = File("../contracts/request_fidelity_v1.json").readBytes()
    private val contract = Json.parseToJsonElement(bytes.decodeToString()).jsonObject

    @Test
    fun sharedArtifactIdentity() {
        assertEquals("request-fidelity-v1", contract.getValue("version").jsonPrimitive.content)
        assertEquals(
            "6f02088311f50707b9c12154d888f844928c23d359043cb2e5d5654bf7e5eaea",
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun ollamaOrdinaryStructuredAndStreamingPreserveExplicitAndUnsetFieldsAndRetryBytes() = runTest {
        verify(Provider.OLLAMA)
    }

    @Test
    fun openaiOrdinaryStructuredAndStreamingPreserveExplicitAndUnsetFieldsAndRetryBytes() = runTest {
        verify(Provider.OPENAI)
    }

    @Test
    fun omlxOrdinaryStructuredAndStreamingPreserveExplicitAndUnsetFieldsAndRetryBytes() = runTest {
        verify(Provider.OMLX)
    }

    private suspend fun verify(provider: Provider) {
        for (element in contract.getValue("cases").jsonArray) {
            val input = element.jsonObject.getValue("config").jsonObject
            val config = CompletionConfig(
                temperature = input["temperature"]?.jsonPrimitive?.doubleOrNull,
                numCtx = input["num_ctx"]?.jsonPrimitive?.intOrNull,
                maxTokens = input["max_tokens"]?.jsonPrimitive?.intOrNull,
            )
            for (entry in listOf("complete", "object", "legacy", "events")) {
                for (retry in listOf(false, true)) verifyEntry(provider, entry, retry, config)
            }
        }
    }

    private suspend fun verifyEntry(provider: Provider, entry: String, retry: Boolean, config: CompletionConfig) {
        val replies = replies(provider, entry, retry)
        val server = RecoveryScriptedTransport(replies)
        val owned = provider.gateway(server.url, engine = server.engine)
        try {
            val actual = config.copy(recovery = if (retry || entry == "events") policy() else null)
            invoke(owned.value, entry, actual)
            assertEquals(replies.size, server.requests.size)
            if (retry) assertContentEquals(server.requests.first(), server.requests.last())
            assertFields(provider, Json.parseToJsonElement(server.requests.last().decodeToString()).jsonObject, config)
        } finally {
            owned.close()
            server.close()
        }
    }

    private fun replies(provider: Provider, entry: String, retry: Boolean): List<ScriptedReply> {
        val family = if (provider == Provider.OLLAMA) "ollama" else "openai"
        val shared = contract.getValue("responses").jsonObject.getValue("$family-$entry").jsonPrimitive.content
        // Kotlin requires the assistant role; retain shared content and terminal semantics.
        val reply = if (provider == Provider.OLLAMA) {
            shared.replace("\"message\": {", "\"message\": {\"role\":\"assistant\",")
        } else {
            shared
        }
        val error = contract.getValue("retryable_error").jsonObject
        return buildList {
            if (retry) {
                add(
                    ScriptedReply(
                        error.getValue("status").jsonPrimitive.intOrNull ?: error("status"),
                        error.getValue("body").jsonPrimitive.content,
                    ),
                )
            }
            add(ScriptedReply(200, reply))
        }
    }

    private suspend fun invoke(gateway: LlmGateway, entry: String, config: CompletionConfig) {
        val messages = RecoveryTestFixtures.messages
        when (entry) {
            "complete" -> assertEquals("answer", gateway.complete("test-model", messages, config = config).content)

            "object" -> assertEquals(
                "\"answer\"",
                gateway.completeJson("test-model", messages, RecoveryTestFixtures.schema, config)["answer"].toString(),
            )

            "legacy" -> assertEquals(
                "answer",
                gateway.stream("test-model", messages, config = config).toList()
                    .filterIsInstance<GatewayStreamEvent.Content>().joinToString("") { it.text },
            )

            else -> {
                val events = (gateway as com.mojentic.llm.StreamEventsGateway).streamEvents("test-model", messages, config).toList()
                assertTrue(events.none { it is com.mojentic.llm.CompletionStreamEvent.Error })
                assertEquals(
                    "answer",
                    events.filterIsInstance<com.mojentic.llm.CompletionStreamEvent.Content>().joinToString("") { it.text },
                )
            }
        }
    }

    private fun assertFields(provider: Provider, body: JsonObject, config: CompletionConfig) {
        val fields = if (provider == Provider.OLLAMA) body.getValue("options").jsonObject else body
        assertEquals(config.temperature, fields["temperature"]?.jsonPrimitive?.doubleOrNull)
        assertEquals(config.maxTokens, fields[if (provider == Provider.OLLAMA) "num_predict" else "max_tokens"]?.jsonPrimitive?.intOrNull)
        assertEquals(if (provider == Provider.OLLAMA) config.numCtx else null, fields["num_ctx"]?.jsonPrimitive?.intOrNull)
        assertFalse("max_completion_tokens" in body)
        assertFalse("num_predict" in body)
        if (provider == Provider.OLLAMA) assertFalse("max_tokens" in fields)
        for (key in listOf("recovery", "reasoningProfile", "preserveReasoning", "maxToolIterations")) assertFalse(key in body)
    }

    @Test
    fun fixedTemperatureIsNormalizedOnlyWhenSetAndReasoningUsesOneOutputField() = runTest {
        for (temperature in listOf(null, 0.0)) {
            RecoveryScriptedTransport(listOf(Provider.OPENAI.success(false))).use { server ->
                Provider.OPENAI.gateway(server.url, engine = server.engine).use { owned ->
                    owned.value.complete(
                        "o3-mini",
                        RecoveryTestFixtures.messages,
                        config = CompletionConfig(temperature = temperature, maxTokens = 73),
                    )
                    val body = Json.parseToJsonElement(server.requests.single().decodeToString()).jsonObject
                    assertEquals(if (temperature == null) null else 1.0, body["temperature"]?.jsonPrimitive?.doubleOrNull)
                    assertEquals(73, body["max_completion_tokens"]?.jsonPrimitive?.intOrNull)
                    assertFalse("max_tokens" in body)
                }
            }
        }
    }

    @Test
    fun droppedEffortWarnsAndIsAbsentOnTheWire() = runTest {
        val previous = System.out
        val warning = ByteArrayOutputStream()
        try {
            System.setOut(PrintStream(warning))
            RecoveryScriptedTransport(listOf(Provider.OPENAI.success(false))).use { server ->
                Provider.OPENAI.gateway(server.url, engine = server.engine).use { owned ->
                    owned.value.complete(
                        "gpt-4o",
                        RecoveryTestFixtures.messages,
                        config = CompletionConfig(reasoningEffort = ReasoningEffort.LOW),
                    )
                    assertFalse("reasoning_effort" in Json.parseToJsonElement(server.requests.single().decodeToString()).jsonObject)
                }
            }
        } finally {
            System.setOut(previous)
        }
        assertTrue(warning.toString().contains("[WARN] OpenAIGateway: dropping unsupported reasoning effort for gpt-4o"))
    }

    private fun policy(): RecoveryPolicy = RecoveryPolicy(maxAttempts = 2, admission = { true }, sleep = {}, jitter = { it })
}
