package com.mojentic.llm.recovery

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Closed identity of the completion adapter that produced recovery evidence. */
@Serializable
public enum class RecoveryProvider {
    /** Ollama chat endpoint. */
    @SerialName("ollama")
    OLLAMA,

    /** oMLX chat completions endpoint. */
    @SerialName("omlx")
    OMLX,

    /** OpenAI chat completions endpoint. */
    @SerialName("openai")
    OPENAI,

    /** Anthropic messages endpoint. */
    @SerialName("anthropic")
    ANTHROPIC,
}
