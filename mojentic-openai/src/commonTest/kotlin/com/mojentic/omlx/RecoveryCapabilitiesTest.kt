package com.mojentic.omlx

import com.mojentic.llm.LlmGateway
import com.mojentic.llm.recovery.RecoveryCapabilities
import com.mojentic.llm.recovery.RecoveryCapability
import com.mojentic.llm.recovery.RecoverySupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecoveryCapabilitiesTest {
    @Test
    fun localCancellationDoesNotProveRemoteOrExactAttemptTermination() {
        val adapter = OmlxGateway(host = "http://localhost:8000")
        try {
            val gateway: LlmGateway = adapter
            val capabilities = gateway.recoveryCapabilities
            assertEquals(RecoverySupport.SUPPORTED, capabilities.localRequestCancellation.status)
            assertEquals(RecoverySupport.UNSUPPORTED, capabilities.remoteRequestCancellation.status)
            assertEquals(RecoverySupport.UNSUPPORTED, capabilities.requestStatus.status)
            assertEquals(RecoverySupport.UNKNOWN, capabilities.idempotency.status)
            assertEquals(RecoverySupport.UNKNOWN, capabilities.exactAttemptTermination.status)
            assertEquals("oMLX POST /v1/chat/completions", capabilities.exactAttemptTermination.endpointScope)
            assertEquals("2026-10-17", capabilities.exactAttemptTermination.inspectedOn)
            assertTrue(capabilities.exactAttemptTermination.documentation.contains("https://github.com/jundot/omlx/blob/main/README.md"))
            assertTrue(capabilities.exactAttemptTermination.rationale.contains("aggregate"))
            assertTrue(capabilities.localRequestCancellation.rationale.contains("coroutine"))
        } finally {
            adapter.close()
        }
    }

    @Test
    fun localRequestCancellationIsSupportedWithoutRemoteTermination() {
        withCapabilities { capabilities ->
            assertFacility(capabilities.localRequestCancellation, RecoverySupport.SUPPORTED, "coroutine cancellation")
        }
    }

    @Test
    fun remoteRequestCancellationIsUnsupported() {
        withCapabilities { capabilities ->
            assertFacility(capabilities.remoteRequestCancellation, RecoverySupport.UNSUPPORTED, "request-scoped remote cancellation")
        }
    }

    @Test
    fun requestStatusIsUnsupported() {
        withCapabilities { capabilities ->
            assertFacility(capabilities.requestStatus, RecoverySupport.UNSUPPORTED, "aggregate evidence")
        }
    }

    @Test
    fun idempotencyIsUnknownWithoutDeduplicationGuarantee() {
        withCapabilities { capabilities ->
            assertFacility(capabilities.idempotency, RecoverySupport.UNKNOWN, "correlation IDs are not idempotency keys")
        }
    }

    @Test
    fun exactAttemptTerminationIsUnknownWithoutAttemptEvidence() {
        withCapabilities { capabilities ->
            assertFacility(
                capabilities.exactAttemptTermination,
                RecoverySupport.UNKNOWN,
                "HTTP closure and model-level aggregate activity are insufficient",
            )
        }
    }

    private fun withCapabilities(assertions: (RecoveryCapabilities) -> Unit) {
        val adapter = OmlxGateway(host = "http://localhost:8000")
        try {
            val gateway: LlmGateway = adapter
            assertions(gateway.recoveryCapabilities)
        } finally {
            adapter.close()
        }
    }

    private fun assertFacility(facility: RecoveryCapability, expected: RecoverySupport, evidence: String) {
        assertEquals(expected, facility.status)
        assertEquals("oMLX POST /v1/chat/completions", facility.endpointScope)
        assertEquals("2026-10-17", facility.inspectedOn)
        assertEquals(
            listOf(
                "https://github.com/jundot/omlx/blob/main/README.md",
                "https://ktor.io/docs/client-requests.html#cancel-request",
            ),
            facility.documentation,
        )
        assertTrue(facility.rationale.contains(evidence), facility.rationale)
    }
}
