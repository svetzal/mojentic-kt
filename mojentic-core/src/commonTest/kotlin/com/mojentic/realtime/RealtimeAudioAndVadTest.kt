package com.mojentic.realtime

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeAudioAndVadTest {

    @Test
    fun sendAudioStreamsFramesAsAppendEvents() = runTest {
        val gateway = FakeRealtimeGateway()
        val broker = RealtimeVoiceBroker(gateway)
        val session = broker.connect(
            model = "gpt-realtime",
            config = RealtimeVoiceConfig(),
            scope = backgroundScope,
        )
        val gs = gateway.openedSessions.single()

        val frames = listOf(
            AudioFrame(shortArrayOf(100, 200, 300)),
            AudioFrame(shortArrayOf(400, -500, 600)),
        )
        session.sendAudio(flowOf(frames[0], frames[1])).join()

        val firstSent = gs.sent.receive() as ClientRealtimeEvent.InputAudioBufferAppend
        assertContentEquals(frames[0].samples, firstSent.frame.samples)
        val secondSent = gs.sent.receive() as ClientRealtimeEvent.InputAudioBufferAppend
        assertContentEquals(frames[1].samples, secondSent.frame.samples)

        session.close()
    }

    @Test
    fun manualCommitSendsBufferCommitAndResponseCreate() = runTest {
        val gateway = FakeRealtimeGateway()
        val broker = RealtimeVoiceBroker(gateway)
        val session = broker.connect(
            model = "gpt-realtime",
            config = RealtimeVoiceConfig(turnDetection = VadConfig.Manual),
            scope = backgroundScope,
        )
        val gs = gateway.openedSessions.single()

        session.commit()
        val first = gs.sent.receive()
        assertTrue(first is ClientRealtimeEvent.InputAudioBufferCommit)
        val second = gs.sent.receive()
        assertTrue(second is ClientRealtimeEvent.ResponseCreate)

        session.close()
    }

    @Test
    fun clearAudioSendsBufferClear() = runTest {
        val gateway = FakeRealtimeGateway()
        val broker = RealtimeVoiceBroker(gateway)
        val session = broker.connect(
            model = "gpt-realtime",
            config = RealtimeVoiceConfig(),
            scope = backgroundScope,
        )
        val gs = gateway.openedSessions.single()

        session.clearAudio()
        val first = gs.sent.receive()
        assertTrue(first is ClientRealtimeEvent.InputAudioBufferClear)

        session.close()
    }

    @Test
    fun userSpeechStartedMidTurnTriggersBargeInAndCancelsResponse() = runTest {
        val gateway = FakeRealtimeGateway()
        val broker = RealtimeVoiceBroker(gateway)
        val session = broker.connect(
            model = "gpt-realtime",
            config = RealtimeVoiceConfig(),
            scope = backgroundScope,
        )
        val gs = gateway.openedSessions.single()

        session.events.test {
            // Open an assistant turn.
            gs.emit("""{"type":"response.created","response":{"id":"r1"}}""")
            assertTrue(awaitItem() is RealtimeEvent.AssistantTurnStarted)
            // User starts speaking mid-response.
            gs.emit("""{"type":"input_audio_buffer.speech_started","item_id":"u1"}""")
            assertTrue(awaitItem() is RealtimeEvent.UserSpeechStarted)
            val interrupted = awaitItem() as RealtimeEvent.Interrupted
            assertEquals("r1", interrupted.turnId)
            assertEquals(RealtimeEvent.InterruptionReason.BargeIn, interrupted.reason)
            cancelAndIgnoreRemainingEvents()
        }

        // Verify ResponseCancel went out on the wire.
        var sawCancel = false
        repeat(MAX_DRAIN_LOOPS) {
            val sent = gs.sent.tryReceive().getOrNull() ?: return@repeat
            if (sent is ClientRealtimeEvent.ResponseCancel) sawCancel = true
        }
        assertTrue(sawCancel, "barge-in should send response.cancel to the provider")

        session.close()
    }

    @Test
    fun userSpeechStartedBeforeTurnDoesNotTriggerBargeIn() = runTest {
        val gateway = FakeRealtimeGateway()
        val broker = RealtimeVoiceBroker(gateway)
        val session = broker.connect(
            model = "gpt-realtime",
            config = RealtimeVoiceConfig(),
            scope = backgroundScope,
        )
        val gs = gateway.openedSessions.single()

        session.events.test {
            gs.emit("""{"type":"input_audio_buffer.speech_started","item_id":"u1"}""")
            assertTrue(awaitItem() is RealtimeEvent.UserSpeechStarted)
            // No Interrupted follows because no turn was in flight.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(gs.sent.tryReceive().getOrNull() == null, "no client events should be sent")

        session.close()
    }

    @Test
    fun audioDeltaIsDecodedToAudioFrameEvent() = runTest {
        val gateway = FakeRealtimeGateway()
        val broker = RealtimeVoiceBroker(gateway)
        val session = broker.connect(
            model = "gpt-realtime",
            config = RealtimeVoiceConfig(),
            scope = backgroundScope,
        )
        val gs = gateway.openedSessions.single()

        // PCM16 little-endian: samples [256, -256] → bytes 00 01 00 FF → base64 "AAEA/w=="
        val base64 = "AAEA/w=="

        session.events.test {
            gs.emit("""{"type":"response.created","response":{"id":"r1"}}""")
            assertTrue(awaitItem() is RealtimeEvent.AssistantTurnStarted)
            gs.emit("""{"type":"response.audio.delta","response_id":"r1","delta":"$base64"}""")
            val audio = awaitItem() as RealtimeEvent.AssistantAudioDelta
            assertEquals("r1", audio.turnId)
            assertContentEquals(shortArrayOf(256, -256), audio.frame.samples)
            cancelAndIgnoreRemainingEvents()
        }

        session.close()
    }

    private companion object {
        const val MAX_DRAIN_LOOPS = 16
    }
}
