package com.mojentic.ollama

import com.mojentic.llm.LlmGateway
import com.mojentic.llm.recovery.RecoveryCapabilities
import com.mojentic.llm.recovery.RecoveryCapability
import com.mojentic.llm.recovery.RecoverySupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecoveryCapabilitiesTest {
    @Test
    fun localRequestCancellationIsSupportedWithoutRemoteTermination() {
        withCapabilities { capabilities ->
            assertFacility(capabilities.localRequestCancellation, RecoverySupport.SUPPORTED, "coroutine cancellation")
        }
    }

    @Test
    fun remoteRequestCancellationIsUnsupported() {
        withCapabilities { capabilities ->
            assertFacility(capabilities.remoteRequestCancellation, RecoverySupport.UNSUPPORTED, "model unload")
        }
    }

    @Test
    fun requestStatusIsUnsupported() {
        withCapabilities { capabilities ->
            assertFacility(capabilities.requestStatus, RecoverySupport.UNSUPPORTED, "/api/ps")
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
        val adapter = OllamaGateway()
        try {
            val gateway: LlmGateway = adapter
            assertions(gateway.recoveryCapabilities)
        } finally {
            adapter.close()
        }
    }

    private fun assertFacility(facility: RecoveryCapability, expected: RecoverySupport, evidence: String) {
        assertEquals(expected, facility.status)
        assertEquals("Ollama POST /api/chat", facility.endpointScope)
        assertEquals("2026-10-17", facility.inspectedOn)
        assertEquals(
            listOf(
                "https://docs.ollama.com/api/chat",
                "https://docs.ollama.com/api/ps",
                "https://ktor.io/docs/client-requests.html#cancel-request",
            ),
            facility.documentation,
        )
        assertTrue(facility.rationale.contains(evidence), facility.rationale)
    }
}
