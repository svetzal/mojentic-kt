package com.mojentic.examples

import com.mojentic.ollama.OllamaGateway
import kotlinx.coroutines.runBlocking

/** Prints the locally available Ollama models, alphabetically sorted. */
fun main() = runBlocking {
    val gateway = OllamaGateway()
    try {
        gateway.availableModels().forEach(::println)
    } finally {
        gateway.close()
    }
}
