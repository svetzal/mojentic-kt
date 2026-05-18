package com.mojentic.openai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JtokkitTokenizerGatewayTest {
    @Test
    fun countsTokensForKnownModel() {
        val tokenizer = JtokkitTokenizerGateway("gpt-4o-mini")

        val count = tokenizer.countTokens("Hello, world!")

        assertTrue(count in 1..10, "expected a small positive token count, was $count")
    }

    @Test
    fun encodeDecodeRoundTrips() {
        val tokenizer = JtokkitTokenizerGateway("gpt-4o")
        val text = "Mojentic ships in Kotlin."

        val tokens = tokenizer.encode(text)
        val decoded = tokenizer.decode(tokens)

        assertEquals(text, decoded)
    }

    @Test
    fun fallsBackToO200kForUnknownModel() {
        // Should not throw; just resolves to the default encoding.
        val tokenizer = JtokkitTokenizerGateway("future-unknown-model-1")
        assertTrue(tokenizer.countTokens("hello") > 0)
    }
}
