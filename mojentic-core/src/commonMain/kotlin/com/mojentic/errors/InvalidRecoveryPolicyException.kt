package com.mojentic.errors

/** The violated recovery constraint; contains no caller values, callbacks or request evidence. */
public enum class RecoveryPolicyViolation {
    /** At least one wire attempt must be permitted. */
    NON_POSITIVE_MAX_ATTEMPTS,

    /** The initial backoff delay must be nonnegative. */
    NEGATIVE_BASE_DELAY,

    /** The delay ceiling must be at least the base delay. */
    CEILING_BELOW_BASE_DELAY,

    /** The ceiling must be below Long.MAX_VALUE so inclusive jitter cannot overflow. */
    UNBOUNDED_DELAY_CEILING,

    /** A configured duration budget must be nonnegative. */
    NEGATIVE_BUDGET,
}

/**
 * Raised synchronously by [com.mojentic.llm.recovery.RecoveryPolicy] construction
 * before any completion request, admission, capture, lifecycle callback or tool execution.
 *
 * All completion entrypoints accept only an already validated policy. This error
 * replaces IllegalArgumentException for invalid policy values. [violation] is a
 * stable, payload-free constraint code; no submitted values or causes are retained.
 */
public class InvalidRecoveryPolicyException(public val violation: RecoveryPolicyViolation) :
    MojenticException("Invalid recovery policy: $violation")
