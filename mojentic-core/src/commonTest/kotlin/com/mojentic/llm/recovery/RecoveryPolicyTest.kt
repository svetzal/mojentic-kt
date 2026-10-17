package com.mojentic.llm.recovery

import com.mojentic.errors.InvalidRecoveryPolicyException
import com.mojentic.errors.RecoveryPolicyViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RecoveryPolicyTest {
    @Test
    fun zeroAttemptsHasTypedConstraint() {
        rejects(RecoveryPolicyViolation.NON_POSITIVE_MAX_ATTEMPTS) { RecoveryPolicy(maxAttempts = 0) }
    }

    @Test
    fun negativeAttemptsHasTypedConstraint() {
        rejects(RecoveryPolicyViolation.NON_POSITIVE_MAX_ATTEMPTS) { RecoveryPolicy(maxAttempts = Int.MIN_VALUE) }
    }

    @Test
    fun negativeBaseDelayHasTypedConstraint() {
        rejects(RecoveryPolicyViolation.NEGATIVE_BASE_DELAY) { RecoveryPolicy(baseDelayMillis = -1) }
    }

    @Test
    fun ceilingBelowBaseHasTypedConstraint() {
        rejects(RecoveryPolicyViolation.CEILING_BELOW_BASE_DELAY) { RecoveryPolicy(baseDelayMillis = 1, delayCeilingMillis = 0) }
    }

    @Test
    fun negativeCeilingHasTypedConstraint() {
        rejects(RecoveryPolicyViolation.CEILING_BELOW_BASE_DELAY) { RecoveryPolicy(baseDelayMillis = 0, delayCeilingMillis = -1) }
    }

    @Test
    fun maximumLongCeilingHasTypedConstraint() {
        rejects(RecoveryPolicyViolation.UNBOUNDED_DELAY_CEILING) { RecoveryPolicy(delayCeilingMillis = Long.MAX_VALUE) }
    }

    @Test
    fun negativeBudgetHasTypedConstraint() {
        rejects(RecoveryPolicyViolation.NEGATIVE_BUDGET) { RecoveryPolicy(budgetMillis = -1) }
    }

    @Test
    fun multipleInvalidValuesReportFirstConstraintWithoutInvokingCallbacks() {
        rejects(RecoveryPolicyViolation.NON_POSITIVE_MAX_ATTEMPTS) {
            RecoveryPolicy(maxAttempts = 0, baseDelayMillis = -1, delayCeilingMillis = Long.MAX_VALUE, budgetMillis = -1)
        }
    }

    @Test
    fun defaultsRemainOneAttemptWithOptionalLimitsAbsent() {
        val policy = RecoveryPolicy()
        assertEquals(1, policy.maxAttempts)
        assertEquals(100, policy.baseDelayMillis)
        assertEquals(30_000, policy.delayCeilingMillis)
        assertNull(policy.budgetMillis)
        assertNull(policy.deadlineEpochMillis)
        assertEquals(setOf(429, 500, 502, 503, 504), policy.retryableStatuses)
        assertEquals(true, policy.retryTransport)
    }

    @Test
    fun zeroDelaysAndBudgetAreValidWithoutCallingHooks() {
        val policy = RecoveryPolicy(
            baseDelayMillis = 0,
            delayCeilingMillis = 0,
            budgetMillis = 0,
            admission = { error("admission must not run at construction") },
            capture = { error("capture must not run at construction") },
            observer = { error("observer must not run at construction") },
            clock = { error("clock must not run at construction") },
            monotonicClock = { error("monotonic clock must not run at construction") },
            sleep = { error("sleep must not run at construction") },
            jitter = { error("jitter must not run at construction") },
        )
        assertEquals(0, policy.baseDelayMillis)
        assertEquals(0, policy.delayCeilingMillis)
        assertEquals(0, policy.budgetMillis)
    }

    @Test
    fun maximumValidValuesAndEqualDelaysArePreserved() {
        val policy = RecoveryPolicy(
            maxAttempts = Int.MAX_VALUE,
            baseDelayMillis = Long.MAX_VALUE - 1,
            delayCeilingMillis = Long.MAX_VALUE - 1,
            budgetMillis = Long.MAX_VALUE,
        )
        assertEquals(Int.MAX_VALUE, policy.maxAttempts)
        assertEquals(Long.MAX_VALUE - 1, policy.baseDelayMillis)
        assertEquals(policy.baseDelayMillis, policy.delayCeilingMillis)
        assertEquals(Long.MAX_VALUE, policy.budgetMillis)
    }

    @Test
    fun pastDeadlinesAndCallerStatusesRemainUnrestricted() {
        val policy = RecoveryPolicy(deadlineEpochMillis = Long.MIN_VALUE, retryableStatuses = emptySet())
        assertEquals(Long.MIN_VALUE, policy.deadlineEpochMillis)
        assertEquals(emptySet(), policy.retryableStatuses)
    }

    private fun rejects(violation: RecoveryPolicyViolation, construct: () -> RecoveryPolicy) {
        val failure = assertFailsWith<InvalidRecoveryPolicyException> { construct() }
        assertEquals(violation, failure.violation)
        assertNull(failure.cause)
        assertEquals(emptyList(), failure.suppressedExceptions)
    }
}
