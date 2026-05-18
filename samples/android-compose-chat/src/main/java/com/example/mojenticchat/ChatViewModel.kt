package com.example.mojenticchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mojentic.chat.ChatSession
import com.mojentic.llm.LlmBroker
import com.mojentic.openai.OpenAiGateway
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds a [ChatSession] for the lifetime of the screen. Streams tokens directly into
 * the `messages` state so the Compose tree recomposes as the model writes.
 *
 * Lives in the consumer's app, not in `mojentic-core` — the library deliberately does
 * not depend on `androidx.lifecycle`.
 */
class ChatViewModel(
    apiKey: String,
    model: String = "gpt-4o-mini",
    systemPrompt: String = "You are a concise assistant. Reply in one or two sentences.",
) : ViewModel() {

    private val gateway = OpenAiGateway(apiKey = apiKey)
    private val broker = LlmBroker(model = model, gateway = gateway)
    private val chat = ChatSession(broker = broker, systemPrompt = systemPrompt)

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private var inFlight: Job? = null

    fun send(text: String) {
        if (text.isBlank()) return
        cancelInFlight()
        _messages.update { it + UiMessage(role = Role.User, content = text) }
        val assistantIndex = _messages.value.size
        _messages.update { it + UiMessage(role = Role.Assistant, content = "") }

        inFlight = viewModelScope.launch {
            try {
                chat.sendStream(text).collect { chunk ->
                    _messages.update { msgs ->
                        msgs.mapIndexed { i, m ->
                            if (i == assistantIndex) m.copy(content = m.content + chunk) else m
                        }
                    }
                }
            } catch (t: Throwable) {
                _messages.update { msgs ->
                    msgs.mapIndexed { i, m ->
                        if (i == assistantIndex) m.copy(content = "[error: ${t.message}]") else m
                    }
                }
            }
        }
    }

    fun cancelInFlight() {
        inFlight?.cancel()
        inFlight = null
    }

    override fun onCleared() {
        gateway.close()
        super.onCleared()
    }
}

data class UiMessage(val role: Role, val content: String)
enum class Role { User, Assistant }
