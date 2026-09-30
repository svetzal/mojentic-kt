package com.mojentic.openai

import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.StreamErrorReason
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertIs

class OpenAIStreamEventParserTest {
    @Test
    fun malformedEvidenceAndToolFieldsAreInvalid() {
        val frames = listOf(
            """{"model":42,"choices":[{"delta":{}}]}""",
            """{"usage":[],"choices":[]}""",
            """{"choices":[{"delta":{},"finish_reason":42}]}""",
            """{"choices":[{"delta":{"tool_calls":"bad"}}]}""",
        )
        for (frame in frames) {
            val parser = OpenAIStreamEventParser(Json)
            val error = assertIs<CompletionStreamEvent.Error>(parser.accept("data: $frame").single())
            assertIs<StreamErrorReason.InvalidStreamEvent>(error.reason)
        }
    }
}
