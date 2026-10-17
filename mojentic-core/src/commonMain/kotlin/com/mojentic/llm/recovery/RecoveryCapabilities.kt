package com.mojentic.llm.recovery

/** Evidence strength for a completion recovery facility; unknown never authorizes replay. */
public enum class RecoverySupport {
    /** The adapter implements the facility in the declared endpoint scope. */
    SUPPORTED,

    /** The adapter has no such facility in the declared endpoint scope. */
    UNSUPPORTED,

    /** The inspected evidence does not establish a guarantee. */
    UNKNOWN,
}

/**
 * Endpoint-scoped facility declaration, not evidence that an individual request terminated.
 *
 * @property status Whether this adapter exposes the facility.
 * @property endpointScope Provider API and endpoint covered, never a caller URL or credential.
 * @property inspectedOn Documentation inspection date (ISO 8601), or null if not inspected.
 * @property documentation Inspected provider documentation URLs; empty if not inspected.
 * @property rationale Why the evidence warrants this status, including limitations.
 */
public data class RecoveryCapability(
    public val status: RecoverySupport = RecoverySupport.UNKNOWN,
    public val endpointScope: String = "Unspecified completion endpoint",
    public val inspectedOn: String? = null,
    public val documentation: List<String> = emptyList(),
    public val rationale: String = "This gateway has not declared evidence for this facility.",
)

/**
 * Recovery facilities of a completion adapter, independent of model capabilities.
 *
 * Local HTTP cancellation does not establish remote cancellation or exact-attempt termination.
 * These declarations neither change admission nor enable retries, headers or provider operations.
 * Defaults keep custom gateways conservative until they supply inspected evidence.
 *
 * @property localRequestCancellation Calling coroutine cancellation interrupts local HTTP work.
 * @property remoteRequestCancellation An adapter operation can cancel a remote inference request.
 * @property requestStatus An adapter operation can inspect an individual in-flight request.
 * @property idempotency An evidenced provider deduplication guarantee for resending a request.
 * @property exactAttemptTermination Evidence can establish termination of this exact attempt.
 */
public data class RecoveryCapabilities(
    public val localRequestCancellation: RecoveryCapability = RecoveryCapability(),
    public val remoteRequestCancellation: RecoveryCapability = RecoveryCapability(),
    public val requestStatus: RecoveryCapability = RecoveryCapability(),
    public val idempotency: RecoveryCapability = RecoveryCapability(),
    public val exactAttemptTermination: RecoveryCapability = RecoveryCapability(),
)
