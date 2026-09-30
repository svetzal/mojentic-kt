package com.mojentic.ollama

import com.mojentic.llm.CompletionEvidence
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.StreamErrorReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OllamaStreamEventParserTest {
    @Test
    fun incompleteStreamKeepsEvidenceAcrossContentOnlyFrames() {
        val parser = OllamaStreamEventParser(Json { ignoreUnknownKeys = true })
        parser.accept(
            """{"model":"reported","message":{"role":"assistant","content":""},"done":false,"prompt_eval_count":9,"total_duration":5000}""",
        )
        parser.accept("""{"message":{"role":"assistant","content":"part"},"done":false,"eval_count":3}""")
        val evidence = CompletionEvidence(
            providerModel = "reported",
            usage = buildJsonObject {
                put("prompt_eval_count", 9)
                put("eval_count", 3)
            },
            metadata = buildJsonObject { put("total_duration", 5000) },
        )
        assertEquals(CompletionStreamEvent.Error(StreamErrorReason.IncompleteStream(evidence)), parser.endOfStream())
    }

    @Test
    fun numericModelAndFinishReasonAreInvalidEvenWithLenientJson() {
        val frames = listOf(
            """{"model":42,"message":{"role":"assistant","content":""},"done":false}""",
            """{"message":{"role":"assistant","content":""},"done":true,"done_reason":42}""",
        )
        for (frame in frames) {
            val parser = OllamaStreamEventParser(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
            val error = assertIs<CompletionStreamEvent.Error>(parser.accept(frame).single())
            assertIs<StreamErrorReason.InvalidStreamEvent>(error.reason)
        }
    }

    @Test
    fun quotedDoneMarkerIsInvalidEvenWithLenientJson() {
        val parser = OllamaStreamEventParser(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            },
        )
        val events = parser.accept("""{"message":{"role":"assistant","content":""},"done":"true","done_reason":"stop"}""")
        val error = assertIs<CompletionStreamEvent.Error>(events.single())
        assertIs<StreamErrorReason.InvalidStreamEvent>(error.reason)
    }
}
