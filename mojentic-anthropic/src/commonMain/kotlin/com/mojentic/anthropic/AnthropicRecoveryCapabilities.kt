package com.mojentic.anthropic

import com.mojentic.llm.recovery.RecoveryCapabilities
import com.mojentic.llm.recovery.RecoveryCapability
import com.mojentic.llm.recovery.RecoverySupport

/** Inspected completion-only declarations; never used to authorize retries. */
internal val anthropicRecoveryCapabilities: RecoveryCapabilities =
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
            "No remote request cancellation operation is exposed for Messages; batch cancellation is a different API.",
        ),
        requestStatus =
        capability(
            RecoverySupport.UNSUPPORTED,
            "No individual in-flight Messages request status operation is exposed; batch status is a different API.",
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
        endpointScope = "Anthropic Messages POST /v1/messages",
        inspectedOn = "2026-10-17",
        documentation =
        listOf(
            "https://platform.claude.com/docs/en/api/http/messages",
            "https://ktor.io/docs/client-requests.html#cancel-request",
        ),
        rationale = rationale,
    )
