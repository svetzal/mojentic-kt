package com.mojentic.agents

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmGatewayResponse
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.tools.LlmTool

/**
 * LLM-backed agent. Subclasses override [receiveEvent] to map incoming events to
 * prompts and feed the LLM response back into the event stream.
 *
 * Mirrors the Python reference's `BaseAsyncLLMAgent`. The Kotlin port keeps the
 * tool list mutable so subclasses can register tools after construction.
 *
 * @property broker the [LlmBroker] used for generation.
 * @property model the LLM model identifier passed to the broker.
 * @property behaviour the system-prompt persona / instructions.
 * @property config completion-time settings (temperature, tool budget, etc.).
 */
public open class BaseAsyncLlmAgent(
    public val broker: LlmBroker,
    public val model: String,
    public var behaviour: String = "You are a helpful assistant.",
    tools: List<LlmTool> = emptyList(),
    public val config: CompletionConfig = CompletionConfig(),
) : Agent {

    private val mutableTools: MutableList<LlmTool> = tools.toMutableList()

    /** Immutable snapshot of currently registered tools. */
    public val tools: List<LlmTool>
        get() = mutableTools.toList()

    public fun addTool(tool: LlmTool) {
        mutableTools += tool
    }

    /** Build the initial message list — system prompt only by default. */
    protected open fun createInitialMessages(): List<LlmMessage> =
        listOf(LlmMessage.system(behaviour))

    /**
     * Run a single LLM turn with [content] appended as a user message.
     * Returns the full [LlmGatewayResponse] so subclasses can inspect content,
     * tool calls, and structured output.
     */
    public open suspend fun generateResponse(content: String): LlmGatewayResponse {
        val messages = createInitialMessages() + LlmMessage.user(content)
        return broker.complete(
            model = model,
            messages = messages,
            tools = tools,
            config = config,
        )
    }

    /** Default implementation does nothing — override to emit follow-up events. */
    override suspend fun receiveEvent(event: Event): List<Event> = emptyList()
}
