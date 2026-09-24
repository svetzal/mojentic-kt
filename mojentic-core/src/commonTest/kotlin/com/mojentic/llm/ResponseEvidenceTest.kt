package com.mojentic.llm

import com.mojentic.llm.tools.LlmTool
import com.mojentic.tracer.LlmResponseEvent
import com.mojentic.tracer.TracerSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Serializable
private data class Answer(val text: String)

private val reportedUsage = buildJsonObject {
    put("prompt_tokens", 10)
    put("completion_tokens", 4)
    put("total_tokens", 14)
}

private val reportedMetadata = buildJsonObject { put("system_fingerprint", "fp_1") }

private class EvidenceGateway(
    private val response: LlmGatewayResponse,
) : LlmGateway {
    override suspend fun complete(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
    ): LlmGatewayResponse = response

    override suspend fun completeJson(
        model: String,
        messages: List<LlmMessage>,
        schema: JsonObject,
        config: CompletionConfig,
    ): JsonObject = error("completeJsonResponse carries the evidence")

    override suspend fun completeJsonResponse(
        model: String,
        messages: List<LlmMessage>,
        schema: JsonObject,
        config: CompletionConfig,
    ): LlmGatewayResponse = response

    override fun stream(
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>?,
        config: CompletionConfig,
    ): Flow<GatewayStreamEvent> = emptyFlow()

    override suspend fun availableModels(): List<String> = emptyList()
}

class ResponseEvidenceTest {
    private val evidenced = LlmGatewayResponse(
        content = "hello",
        usage = reportedUsage,
        providerModel = "gpt-4o-mini-2024-07-18",
        finishReason = "stop",
        metadata = reportedMetadata,
    )

    private suspend fun TracerSystem.responseEvent(): LlmResponseEvent =
        eventStore.getEvents(LlmResponseEvent::class).single() as LlmResponseEvent

    @Test
    fun responseEventCarriesGatewayEvidenceUnchanged() = runTest {
        val tracer = TracerSystem()
        LlmBroker(EvidenceGateway(evidenced), tracer).generateResponse("configured", listOf(LlmMessage.user("hi")))

        val event = tracer.responseEvent()

        assertEquals("configured", event.model)
        assertEquals(reportedUsage, event.usage)
        assertEquals("gpt-4o-mini-2024-07-18", event.providerModel)
        assertEquals("stop", event.finishReason)
        assertEquals(reportedMetadata, event.metadata)
    }

    @Test
    fun responseEventHasNullUsageWhenGatewayReportsNone() = runTest {
        val tracer = TracerSystem()
        LlmBroker(EvidenceGateway(LlmGatewayResponse(content = "hello")), tracer)
            .complete("configured", listOf(LlmMessage.user("hi")))

        val event = tracer.responseEvent()

        assertNull(event.usage)
        assertNull(event.providerModel)
        assertNull(event.finishReason)
        assertNull(event.metadata)
    }

    @Test
    fun structuredResponseEventCarriesGatewayEvidence() = runTest {
        val tracer = TracerSystem()
        val structured = evidenced.copy(content = """{"text":"hi"}""", structuredJson = buildJsonObject { put("text", "hi") })

        val answer = LlmBroker(EvidenceGateway(structured), tracer)
            .completeJson<Answer>("configured", listOf(LlmMessage.user("hi")))

        val event = tracer.responseEvent()
        assertEquals(Answer("hi"), answer)
        assertEquals(reportedUsage, event.usage)
        assertEquals("gpt-4o-mini-2024-07-18", event.providerModel)
        assertEquals("stop", event.finishReason)
        assertEquals(reportedMetadata, event.metadata)
    }
}
