package com.mojentic.llm.recovery

import kotlinx.serialization.Serializable

/** Serializable failure evidence with no provider text, headers, bytes, or cause messages. */
@Serializable
public data class RecoveryFailureSummary(
    val provider: String,
    val operation: String,
    val identity: RecoveryIdentity,
    val category: RecoveryCategory,
    val status: Int?,
    val progress: RecoveryProgress,
    val retryAfter: RecoveryRetryAfter,
    val reason: RecoveryReason,
    val acceptance: RecoveryAcceptance,
    val phase: RecoveryPhase,
    val eligible: Boolean,
    val wireSent: Boolean,
)

/** Caller can serialize this report without opting into sensitive cause or wire inspection. */
@Serializable
public data class RecoveryReport(
    val reason: RecoveryReason,
    val failures: List<RecoveryFailureSummary>,
    val wireAttempts: Int,
)

/** Safe lifecycle serialization includes actual wire counts and observed progress. */
@Serializable
public data class RecoveryLifecycleSummary(
    val stage: RecoveryStage,
    val identity: RecoveryIdentity,
    val delayMillis: Long?,
    val failures: List<RecoveryFailureSummary>,
    val wireAttempts: Int,
    val progress: RecoveryProgress?,
)
