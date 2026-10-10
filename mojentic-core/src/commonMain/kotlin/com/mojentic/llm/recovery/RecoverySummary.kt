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
    val frameIndex: Long? = null,
    val metrics: RecoveryStreamMetrics? = null,
) {
    /** Retains the pre-streaming constructor and its default-argument bridge. */
    public constructor(
        stage: RecoveryStage,
        identity: RecoveryIdentity,
        delayMillis: Long?,
        failures: List<RecoveryFailureSummary>,
        wireAttempts: Int,
        progress: RecoveryProgress?,
    ) : this(stage, identity, delayMillis, failures, wireAttempts, progress, null, null)

    /** Retains the pre-streaming copy signature while preserving streaming evidence. */
    public fun copy(
        stage: RecoveryStage = this.stage,
        identity: RecoveryIdentity = this.identity,
        delayMillis: Long? = this.delayMillis,
        failures: List<RecoveryFailureSummary> = this.failures,
        wireAttempts: Int = this.wireAttempts,
        progress: RecoveryProgress? = this.progress,
    ): RecoveryLifecycleSummary = RecoveryLifecycleSummary(
        stage,
        identity,
        delayMillis,
        failures,
        wireAttempts,
        progress,
        frameIndex,
        metrics,
    )
}
