package com.mojentic.examples

import com.mojentic.realtime.RealtimeEvent
import com.mojentic.realtime.RealtimeModality
import com.mojentic.realtime.RealtimeVoiceBroker
import com.mojentic.realtime.RealtimeVoiceConfig
import com.mojentic.realtime.VadConfig
import com.mojentic.realtime.openai.OpenAiRealtimeGateway
import kotlinx.coroutines.runBlocking

/**
 * Connects to OpenAI Realtime in text-only modality and demonstrates the
 * vendor-neutral event flow: send a user message, observe assistant streaming
 * deltas, observe the turn-completed event.
 *
 * Audio modality and tool calling are exercised in [RealtimeAudioAndVadTest]
 * and `RealtimeSessionTest` (offline, scripted). This example shows the
 * developer-facing API against a live OpenAI Realtime session.
 *
 * Requires:
 *  - `OPENAI_API_KEY` environment variable
 *  - `MOJENTIC_REALTIME_MODEL` (defaults to `gpt-4o-realtime-preview`)
 */
fun main(): Unit = runBlocking {
    val apiKey = System.getenv("OPENAI_API_KEY")
        ?: error("Set OPENAI_API_KEY to run this example")
    val model = System.getenv("MOJENTIC_REALTIME_MODEL") ?: "gpt-4o-realtime-preview"

    val gateway = OpenAiRealtimeGateway(apiKey = apiKey)
    val broker = RealtimeVoiceBroker(gateway)
    val config = RealtimeVoiceConfig(
        instructions = "You are a concise voice assistant. Keep replies under three sentences.",
        modalities = setOf(RealtimeModality.TEXT),
        turnDetection = VadConfig.Manual,
    )

    val session = broker.connect(model = model, config = config)
    try {
        session.sendText("Give me one fun fact about octopuses.")
        session.events.collect { event ->
            when (event) {
                is RealtimeEvent.AssistantTextDelta -> print(event.delta)
                is RealtimeEvent.AssistantText -> println("\n[final] ${event.text}")
                is RealtimeEvent.AssistantTurnCompleted -> {
                    println("\n[turn ${event.turnId} done; tokens=${event.totalTokens ?: "?"}]")
                    return@collect
                }
                is RealtimeEvent.GatewayError -> {
                    println("\n[error] ${event.message}")
                    return@collect
                }
                else -> Unit
            }
        }
    } finally {
        session.close()
    }
}
