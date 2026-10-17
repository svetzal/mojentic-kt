package com.mojentic.ollama

import com.mojentic.llm.CompletionStreamEvent
import kotlinx.serialization.json.JsonObject

/** Existing provider/order regressions exclude the new client measurements, tested exactly by ClientStreamTimingHttpTest. */
internal fun List<CompletionStreamEvent>.providerEvidence(): List<CompletionStreamEvent> = map { event ->
    when (event) {
        is CompletionStreamEvent.Completed -> event.copy(
            evidence = event.evidence.copy(metadata = event.evidence.metadata.providerMetrics()),
        )

        is CompletionStreamEvent.Error -> event.copy(evidence = null)

        is CompletionStreamEvent.Content -> event
    }
}

internal fun JsonObject?.providerMetrics(): JsonObject? = this?.let {
    JsonObject(it.filterKeys { key -> key !in setOf("request_duration_ms", "time_to_first_token_ms", "first_token_kind") })
        .takeIf { fields -> fields.isNotEmpty() }
}
