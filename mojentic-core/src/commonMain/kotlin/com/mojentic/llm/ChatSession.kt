package com.mojentic.llm

import com.mojentic.llm.tools.LlmTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Multi-turn chat coordinator on top of an [LlmBroker].
 *
 * Owns the message history, an optional system prompt, and a default tool set
 * so callers can send a single user prompt per turn without rebuilding the
 * full message list each time. Mutable state is protected by a [Mutex] so the
 * session is safe to share across coroutines.
 *
 * Two surfaces mirror the broker:
 * - [send] — suspending, single-shot turn returning the assistant text.
 * - [stream] — cold `Flow` of [StreamEvent]; history is updated atomically
 *   once the flow completes.
 *
 * With [CompletionConfig.preserveReasoning], completed assistant/tool transcripts
 * retain native reasoning for subsequent user turns. Failed or cancelled turns
 * roll back without adding native history.
 *
 * The session is the Kotlin analogue of the Python reference's `ChatSession`.
 *
 * Recovery configuration is validated when [com.mojentic.llm.recovery.RecoveryPolicy]
 * is constructed. Invalid values throw [com.mojentic.errors.InvalidRecoveryPolicyException]
 * before any completion, callback or tool execution; these APIs accept only validated policies.
 */
public class ChatSession(
    private val broker: LlmBroker,
    private val model: String,
    systemPrompt: String? = null,
    private val tools: List<LlmTool> = emptyList(),
    private val config: CompletionConfig = CompletionConfig(),
) {
    private val mutex = Mutex()
    private val history: MutableList<LlmMessage> = mutableListOf<LlmMessage>().apply {
        if (systemPrompt != null) add(LlmMessage.system(systemPrompt))
    }

    /**
     * Send a user message and return the assistant's response.
     *
     * History is updated atomically: the user message is appended before the
     * broker call and the assistant is appended on success. When native retention
     * is selected, the completed assistant/tool transcript is appended instead.
     * On failure the session is reset to the state before the call.
     */
    public suspend fun send(message: String): LlmGatewayResponse {
        val (snapshot, conversation) = mutex.withLock {
            val before = history.toList()
            history += LlmMessage.user(message)
            before to history.toList()
        }
        return try {
            var completed: List<LlmMessage>? = null
            val response = broker.completeConversation(model, conversation, tools, config, null) { completed = it }
            mutex.withLock {
                if (config.preserveReasoning) {
                    history += completed.orEmpty().drop(conversation.size)
                } else {
                    history += LlmMessage.assistant(content = response.content, toolCalls = response.toolCalls.takeIf { it.isNotEmpty() })
                }
            }
            response
        } catch (t: Throwable) {
            mutex.withLock {
                history.clear()
                history.addAll(snapshot)
            }
            throw t
        }
    }

    /**
     * Send a user message and stream the assistant's response.
     *
     * History is updated once the flow completes successfully. If the
     * collecting coroutine is cancelled, the user message is rolled back so
     * the session reflects only fully-completed turns.
     */
    public fun stream(message: String): Flow<StreamEvent> = flow {
        val (snapshot, conversation) = mutex.withLock {
            val before = history.toList()
            history += LlmMessage.user(message)
            before to history.toList()
        }
        val contentBuilder = StringBuilder()
        val toolCalls = mutableListOf<LlmToolCall>()
        try {
            var completed: List<LlmMessage>? = null
            broker.streamConversation(model, conversation, tools, config, null) { completed = it }.collect { event ->
                when (event) {
                    is StreamEvent.TextChunk -> contentBuilder.append(event.text)
                    is StreamEvent.ToolCall -> toolCalls += event.call
                    is StreamEvent.ToolResult, is StreamEvent.ThinkingChunk, is StreamEvent.Completed -> Unit
                }
                emit(event)
            }
            mutex.withLock {
                if (config.preserveReasoning) {
                    history += completed.orEmpty().drop(conversation.size)
                } else {
                    history += LlmMessage.assistant(
                        content = contentBuilder.toString().ifEmpty { null },
                        toolCalls = toolCalls.takeIf { it.isNotEmpty() },
                    )
                }
            }
        } catch (t: Throwable) {
            mutex.withLock {
                history.clear()
                history.addAll(snapshot)
            }
            throw t
        }
    }

    /** Return an immutable snapshot of the current message history. */
    public suspend fun messages(): List<LlmMessage> = mutex.withLock { history.toList() }

    /** Drop all non-system messages. The system prompt (if any) is preserved. */
    public suspend fun reset() {
        mutex.withLock {
            val systemPrompt = history.firstOrNull { it.role == MessageRole.System }
            history.clear()
            if (systemPrompt != null) history += systemPrompt
        }
    }
}
