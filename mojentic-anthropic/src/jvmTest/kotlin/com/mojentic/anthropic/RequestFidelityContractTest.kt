package com.mojentic.anthropic

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.recovery.RecoveryPolicy
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Anthropic projection of shared fidelity inputs; local transport evidence, not provider verification. */
class RequestFidelityContractTest {
    private val bytes = File("../contracts/request_fidelity_v1.json").readBytes()
    private val contract = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
    private val messages = listOf(LlmMessage.user("contract request"))
    private val schema = Json.parseToJsonElement("""{"type":"object","properties":{"answer":{"type":"string"}}}""").jsonObject
    private val answer = Json.parseToJsonElement(
        contract.getValue("responses").jsonObject.getValue("openai-complete").jsonPrimitive.content,
    )
        .jsonObject.getValue(
            "choices",
        ).jsonArray.first().jsonObject.getValue("message").jsonObject.getValue("content").jsonPrimitive.content

    @Test
    fun sharedArtifactIdentity() {
        assertEquals("request-fidelity-v1", contract.getValue("version").jsonPrimitive.content)
        assertEquals(
            "6f02088311f50707b9c12154d888f844928c23d359043cb2e5d5654bf7e5eaea",
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun anthropicOrdinaryStructuredAndStreamingPreserveFieldsAndIdenticalRetryPayloads() = runTest {
        for (element in contract.getValue("cases").jsonArray) {
            val fields = element.jsonObject.getValue("config").jsonObject
            val config = CompletionConfig(
                temperature = fields["temperature"]?.jsonPrimitive?.doubleOrNull,
                numCtx = fields["num_ctx"]?.jsonPrimitive?.intOrNull,
                maxTokens = fields["max_tokens"]?.jsonPrimitive?.intOrNull,
            )
            verify("unknown-model", 4096, config)
        }
    }

    @Test
    fun knownRegistryIdsAndAliasesUseMaximumOutputAndUnknownNamesUse4096() = runTest {
        for (model in listOf(
            "claude-sonnet-4-5",
            "claude-sonnet-4-5-20250929",
            "claude-haiku-4-5",
            "claude-haiku-4-5-20251001",
            "unknown-model",
            "claude-sonnet-4-5-future",
        )) {
            val maximum = if (model in listOf("unknown-model", "claude-sonnet-4-5-future")) 4096 else 64_000
            verify(model, maximum, CompletionConfig())
            verify(model, maximum, CompletionConfig(temperature = 0.0, maxTokens = 73))
        }
    }

    private suspend fun verify(model: String, fallback: Int, config: CompletionConfig) {
        for (entry in 0..3) {
            for (retry in listOf(false, true)) verifyEntry(model, fallback, config, entry, retry)
        }
    }

    private suspend fun verifyEntry(model: String, fallback: Int, config: CompletionConfig, entry: Int, retry: Boolean) {
        val replies = replies(entry, retry)
        val server = RecoveryScriptedServer(replies)
        val gateway = AnthropicGateway("contract-key", host = server.url)
        try {
            val actual = config.copy(recovery = if (retry) policy() else null)
            invoke(gateway, model, entry, actual)
            assertEquals(replies.size, server.requests.size)
            if (retry) assertContentEquals(server.requests.first(), server.requests.last())
            val body = Json.parseToJsonElement(server.requests.last().decodeToString()).jsonObject
            assertEquals(config.maxTokens ?: fallback, body["max_tokens"]?.jsonPrimitive?.intOrNull)
            assertEquals(config.temperature, body["temperature"]?.jsonPrimitive?.doubleOrNull)
            if (config.temperature == null) assertFalse("temperature" in body)
            for (key in listOf("num_ctx", "num_predict", "max_completion_tokens", "recovery", "maxToolIterations")) {
                assertFalse(key in body)
            }
        } finally {
            gateway.close()
            server.close()
        }
    }

    private fun replies(entry: Int, retry: Boolean): List<ScriptedReply> {
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
            add(ScriptedReply(200, response(entry)))
        }
    }

    private suspend fun invoke(gateway: AnthropicGateway, model: String, entry: Int, config: CompletionConfig) {
        when (entry) {
            0 -> assertEquals(answer, gateway.complete(model, messages, config = config).content)

            1 -> assertEquals("\"$answer\"", gateway.completeJson(model, messages, schema, config)["answer"].toString())

            2 -> assertEquals(
                "\"$answer\"",
                gateway.completeJsonResponse(model, messages, schema, config).structuredJson?.jsonObject?.get("answer").toString(),
            )

            else -> assertEquals(
                answer,
                gateway.stream(model, messages, config = config).toList()
                    .filterIsInstance<GatewayStreamEvent.Content>().joinToString("") { it.text },
            )
        }
    }

    private fun policy(): RecoveryPolicy = RecoveryPolicy(maxAttempts = 2, admission = { true }, sleep = {}, jitter = { it })

    private fun response(entry: Int): String = when (entry) {
        0 -> """{"content":[{"type":"text","text":"$answer"}],"stop_reason":"end_turn"}"""

        1, 2 ->
            """{"content":[{"type":"tool_use","id":"contract-tool","name":"respond_in_json",""" +
                """"input":{"answer":"$answer"}}],"stop_reason":"tool_use"}"""

        else -> frame("""{"type":"message_start","message":{"content":[]}}""") +
            frame("""{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""") +
            frame("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"$answer"}}""") +
            frame("""{"type":"content_block_stop","index":0}""") +
            frame("""{"type":"message_delta","delta":{"stop_reason":"end_turn"}}""") + frame("""{"type":"message_stop"}""")
    }

    private fun frame(body: String): String = "data: $body\n\n"
}
