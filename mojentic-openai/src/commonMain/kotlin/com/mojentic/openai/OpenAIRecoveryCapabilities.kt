package com.mojentic.openai

import com.mojentic.llm.recovery.RecoveryCapabilities
import com.mojentic.llm.recovery.RecoveryCapability
import com.mojentic.llm.recovery.RecoverySupport

/** Inspected completion-only declarations; never used to authorize retries. */
internal val openAIRecoveryCapabilities: RecoveryCapabilities =
    RecoveryCapabilities(
        localRequestCancellation =
        capability(
            RecoverySupport.SUPPORTED,
            "Default Ktor HTTP work honours calling coroutine cancellation; custom engines own transport " +
                "cleanup. This does not confirm remote inference stopped.",
        ),
        remoteRequestCancellation =
        capability(
            RecoverySupport.UNSUPPORTED,
            "No remote request cancellation operation is exposed for Chat Completions; Responses cancellation is a different API.",
        ),
        requestStatus =
        capability(
            RecoverySupport.UNSUPPORTED,
            "No in-flight request status operation is exposed; retrieval requires store=true and a " +
                "completed chat ID, not an ambiguous attempt.",
        ),
        idempotency =
        capability(
            RecoverySupport.UNKNOWN,
            "Inspected endpoint documentation does not establish a deduplication guarantee for resending " +
                "this request; correlation IDs are not idempotency keys.",
        ),
        exactAttemptTermination =
        capability(
            RecoverySupport.UNKNOWN,
            "No evidence source confirms termination of this exact attempt; HTTP closure and model-level " +
                "aggregate activity are insufficient.",
        ),
    )

private fun capability(
    status: RecoverySupport,
    rationale: String,
): RecoveryCapability =
    RecoveryCapability(
        status = status,
        endpointScope = "OpenAI Chat Completions POST /v1/chat/completions",
        inspectedOn = "2026-10-17",
        documentation =
        listOf(
            "https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create",
            "https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/retrieve",
            "https://ktor.io/docs/client-requests.html#cancel-request",
        ),
        rationale = rationale,
    )
