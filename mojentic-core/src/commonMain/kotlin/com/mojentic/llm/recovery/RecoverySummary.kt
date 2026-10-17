package com.mojentic.llm.recovery

import kotlinx.serialization.Serializable

/** Serializable failure evidence with validated metadata and no raw provider text, headers, bytes, or cause messages. */
@Serializable
public data class RecoveryFailureSummary(
    val provider: RecoveryProvider,
    val operation: RecoveryOperation,
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
    val providerCode: String? = null,
    val providerRequestId: String? = null,
) {
    /** Convenience constructor without provider metadata. */
    @Suppress("LongParameterList") // The summary constructor includes all safe failure fields.
    public constructor(
        provider: RecoveryProvider,
        operation: RecoveryOperation,
        identity: RecoveryIdentity,
        category: RecoveryCategory,
        status: Int?,
        progress: RecoveryProgress,
        retryAfter: RecoveryRetryAfter,
        reason: RecoveryReason,
        acceptance: RecoveryAcceptance,
        phase: RecoveryPhase,
        eligible: Boolean,
        wireSent: Boolean,
    ) : this(
        provider, operation, identity, category, status, progress, retryAfter, reason, acceptance, phase, eligible, wireSent,
        null, null,
    )

    /** Copies the failure fields while preserving validated provider metadata. */
    public fun copy(
        provider: RecoveryProvider = this.provider,
        operation: RecoveryOperation = this.operation,
        identity: RecoveryIdentity = this.identity,
        category: RecoveryCategory = this.category,
        status: Int? = this.status,
        progress: RecoveryProgress = this.progress,
        retryAfter: RecoveryRetryAfter = this.retryAfter,
        reason: RecoveryReason = this.reason,
        acceptance: RecoveryAcceptance = this.acceptance,
        phase: RecoveryPhase = this.phase,
        eligible: Boolean = this.eligible,
        wireSent: Boolean = this.wireSent,
    ): RecoveryFailureSummary = RecoveryFailureSummary(
        provider, operation, identity, category, status, progress, retryAfter, reason, acceptance, phase, eligible, wireSent,
        providerCode, providerRequestId,
    )
}

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
