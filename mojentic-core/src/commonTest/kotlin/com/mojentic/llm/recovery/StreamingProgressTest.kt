package com.mojentic.llm.recovery

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamingProgressTest {
    @Test
    fun escapedIncompleteFieldsBlockReplayBeforeFrameDecoding() {
        for (key in listOf("content", "thinking", "reasoning_content", "tool_calls")) {
            val value = if (key == "tool_calls") "[{" else "\"é"
            val escaped = "\\u" + key.first().code.toString(16).padStart(4, '0') + key.drop(1)
            val body = "{\"message\":{\"$escaped\":$value"
            val progress = streamingProgress(body.encodeToByteArray())
            assertTrue(progress.semanticObserved)
            assertTrue(progress.replayUnsafe)
            assertEquals(body.encodeToByteArray().size.toLong(), progress.rawBytes)
            assertEquals(RecoverySemanticProgress(), progress.observed)
        }
    }

    @Test
    fun emptySemanticFieldsAndQuotedKeyLookalikesRemainReplaySafe() {
        for (body in listOf(
            """{"message":{"\u0063ontent":"","thinking":"","tool_calls":[]}}""",
            """{"metadata":"\"content\":\"fake","message":{"content":""}}""",
            """data: {"choices":[{"delta":{"content":"","reasoning_content":"","tool_calls":[]}}]}""",
        )) {
            assertFalse(streamingProgress(body.encodeToByteArray()).semanticObserved)
        }
    }

    @Test
    fun completedToolEvidenceSurvivesSubsequentWireReads() = runTest {
        val evidence = ResponseEvidence().apply {
            streaming = true
            status = 200
        }
        val stream = RecoveryStream(
            ByteReadChannel("\n"),
            evidence,
            RecoveryIdentity("logical", "attempt", 1),
            RecoveryPolicy(),
            "{}",
        )
        stream.completedTools(1)
        assertEquals("", stream.readLine())
        val progress = evidence.progress()
        assertEquals(1, progress.completedToolCallsObserved)
        assertEquals(1L, progress.observed.completedToolCalls)
        assertTrue(progress.replayUnsafe)
        assertFalse(progress.semanticDelivered)
    }

    @Test
    fun numericProgressIndependentlyVetoesReplay() {
        for (counts in listOf(
            RecoverySemanticProgress(contentBytes = 2),
            RecoverySemanticProgress(reasoningBytes = 2),
            RecoverySemanticProgress(toolFragments = 1),
            RecoverySemanticProgress(completedToolCalls = 1),
        )) {
            val evidence = ResponseEvidence().apply {
                streaming = true
                streamProgress = RecoveryProgress(true, 0, false, observed = counts)
            }
            assertTrue(evidence.progress().replayUnsafe)
            evidence.streamProgress = RecoveryProgress(true, 0, false)
            evidence.deliveredCounts = counts
            assertTrue(evidence.progress().replayUnsafe)
            assertTrue(evidence.progress().semanticDelivered)
        }
    }
}
