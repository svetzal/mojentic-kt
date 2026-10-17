package com.mojentic.omlx

import com.mojentic.llm.recovery.RecoveryCapabilities
import com.mojentic.llm.recovery.RecoveryCapability
import com.mojentic.llm.recovery.RecoverySupport

/** Inspected completion-only declarations; never used to authorize retries. */
internal val omlxRecoveryCapabilities: RecoveryCapabilities =
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
            "No request-scoped remote cancellation operation is exposed by this Chat Completions adapter.",
        ),
        requestStatus =
        capability(
            RecoverySupport.UNSUPPORTED,
            "No request-scoped status operation is exposed; model activity is aggregate evidence, not individual request status.",
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
        endpointScope = "oMLX POST /v1/chat/completions",
        inspectedOn = "2026-10-17",
        documentation =
        listOf(
            "https://github.com/jundot/omlx/blob/main/README.md",
            "https://ktor.io/docs/client-requests.html#cancel-request",
        ),
        rationale = rationale,
    )
