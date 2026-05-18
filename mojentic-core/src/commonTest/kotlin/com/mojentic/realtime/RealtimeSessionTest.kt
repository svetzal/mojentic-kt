package com.mojentic.realtime

import app.cash.turbine.test
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeSessionTest {

    @Test
    fun sendTextEmitsConversationItemAndResponseCreate() = runTest {
        val gateway = FakeRealtimeGateway()
        val broker = RealtimeVoiceBroker(gateway)
        val session = broker.connect(
            model = "gpt-realtime",
            config = RealtimeVoiceConfig(instructions = "be concise"),
            scope = backgroundScope,
        )

        session.sendText("Hello agent.")

        // The fake gateway is a thin transport — initial session.update is
        // sent by the real OpenAi gateway inside `open()`, not the broker.
        // So `sent` here carries only what the session itself sends.
        val text = gateway.openedSessions.single().sent.receive()
        assertTrue(text is ClientRealtimeEvent.UserText)
        assertEquals("Hello agent.", text.text)
        val responseCreate = gateway.openedSessions.single().sent.receive()
        assertTrue(responseCreate is ClientRealtimeEvent.ResponseCreate)

        session.close()
    }

    @Test
    fun normalisesAssistantTextDeltasAndCompletion() = runTest {
        val gateway = FakeRealtimeGateway()
        val broker = RealtimeVoiceBroker(gateway)
        val session = broker.connect(
            model = "gpt-realtime",
            config = RealtimeVoiceConfig(),
            scope = backgroundScope,
        )
        val gs = gateway.openedSessions.single()

        session.events.test {
            gs.emit("""{"type":"response.created","response":{"id":"r1"}}""")
            gs.emit("""{"type":"response.text.delta","response_id":"r1","delta":"Hi"}""")
            gs.emit("""{"type":"response.text.delta","response_id":"r1","delta":" there"}""")
            gs.emit("""{"type":"response.text.done","response_id":"r1","text":"Hi there"}""")
            gs.emit(
                """{"type":"response.done","response":{"id":"r1","usage":{"input_tokens":5,"output_tokens":2,"total_tokens":7}}}""",
            )

            val started = awaitItem() as RealtimeEvent.AssistantTurnStarted
            assertEquals("r1", started.turnId)
            assertEquals("Hi", (awaitItem() as RealtimeEvent.AssistantTextDelta).delta)
            assertEquals(" there", (awaitItem() as RealtimeEvent.AssistantTextDelta).delta)
            assertEquals("Hi there", (awaitItem() as RealtimeEvent.AssistantText).text)
            val completed = awaitItem() as RealtimeEvent.AssistantTurnCompleted
            assertEquals(5, completed.inputTokens)
            assertEquals(2, completed.outputTokens)
            assertEquals(7, completed.totalTokens)
            cancelAndIgnoreRemainingEvents()
        }

        session.close()
    }

    @Test
    fun dispatchesToolCallsViaToolRunnerAndSubmitsOutputs() = runTest {
        val gateway = FakeRealtimeGateway()
        val tool = RecordingTool("get_time", """{"now":"2026-05-18T12:00:00Z"}""")
        val broker = RealtimeVoiceBroker(gateway)
        val session = broker.connect(
            model = "gpt-realtime",
            config = RealtimeVoiceConfig(tools = listOf(tool)),
            scope = backgroundScope,
        )
        val gs = gateway.openedSessions.single()

        session.events.test {
            gs.emit("""{"type":"response.created","response":{"id":"r1"}}""")
            gs.emit(
                """{"type":"response.output_item.added","response_id":"r1","item":{"type":"function_call","call_id":"call_1","name":"get_time"}}""",
            )
            gs.emit("""{"type":"response.function_call_arguments.delta","call_id":"call_1","delta":"{}"}""")
            gs.emit(
                """{"type":"response.function_call_arguments.done","call_id":"call_1","name":"get_time","arguments":"{}"}""",
            )
            gs.emit("""{"type":"response.done","response":{"id":"r1"}}""")

            // Order: AssistantTurnStarted → ToolCallStarted → ToolCallArgsDelta →
            //        ToolCallDispatched → AssistantTurnCompleted →
            //        ToolCallCompleted → ToolBatchSubmitted
            assertTrue(awaitItem() is RealtimeEvent.AssistantTurnStarted)
            assertTrue(awaitItem() is RealtimeEvent.ToolCallStarted)
            assertTrue(awaitItem() is RealtimeEvent.ToolCallArgsDelta)
            val dispatched = awaitItem() as RealtimeEvent.ToolCallDispatched
            assertEquals("call_1", dispatched.callId)
            assertTrue(awaitItem() is RealtimeEvent.AssistantTurnCompleted)
            val completed = awaitItem() as RealtimeEvent.ToolCallCompleted
            assertEquals("call_1", completed.callId)
            assertEquals("""{"now":"2026-05-18T12:00:00Z"}""", completed.result)
            val batch = awaitItem() as RealtimeEvent.ToolBatchSubmitted
            assertEquals(listOf("call_1"), batch.callIds)
            cancelAndIgnoreRemainingEvents()
        }

        var sawFunctionCallOutput = false
        var sawSecondResponseCreate = false
        repeat(MAX_DRAIN_LOOPS) {
            val sent = gs.sent.tryReceive().getOrNull() ?: return@repeat
            if (sent is ClientRealtimeEvent.FunctionCallOutput) {
                assertEquals("call_1", sent.callId)
                assertEquals("""{"now":"2026-05-18T12:00:00Z"}""", sent.output)
                sawFunctionCallOutput = true
            }
            if (sent is ClientRealtimeEvent.ResponseCreate) {
                sawSecondResponseCreate = true
            }
        }
        assertTrue(sawFunctionCallOutput, "session should have submitted function_call_output")
        assertTrue(sawSecondResponseCreate, "session should request another response after tool dispatch")
        assertEquals(1, tool.callCount, "tool should have been executed exactly once")

        session.close()
    }

    @Test
    fun interruptSendsResponseCancelAndEmitsManualInterruption() = runTest {
        val gateway = FakeRealtimeGateway()
        val broker = RealtimeVoiceBroker(gateway)
        val session = broker.connect(
            model = "gpt-realtime",
            config = RealtimeVoiceConfig(),
            scope = backgroundScope,
        )
        val gs = gateway.openedSessions.single()

        session.events.test {
            session.interrupt()
            val event = awaitItem() as RealtimeEvent.Interrupted
            assertEquals(RealtimeEvent.InterruptionReason.Manual, event.reason)
            cancelAndIgnoreRemainingEvents()
        }
        val sent = gs.sent.receive()
        assertTrue(sent is ClientRealtimeEvent.ResponseCancel)

        session.close()
    }

    @Test
    fun closeEmitsSessionClosedEvent() = runTest {
        val gateway = FakeRealtimeGateway()
        val broker = RealtimeVoiceBroker(gateway)
        val session = broker.connect(
            model = "gpt-realtime",
            config = RealtimeVoiceConfig(),
            scope = backgroundScope,
        )
        val gs = gateway.openedSessions.single()

        session.events.test {
            session.close()
            val event = awaitItem() as RealtimeEvent.SessionClosed
            assertEquals(RealtimeEvent.CloseReason.Client, event.reason)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(gs.closed)
    }

    @Test
    fun userTranscriptCompletionIsSurfaced() = runTest {
        val gateway = FakeRealtimeGateway()
        val broker = RealtimeVoiceBroker(gateway)
        val session = broker.connect(
            model = "gpt-realtime",
            config = RealtimeVoiceConfig(),
            scope = backgroundScope,
        )
        val gs = gateway.openedSessions.single()

        session.events.test {
            gs.emit(
                """{"type":"conversation.item.input_audio_transcription.completed","item_id":"i1","transcript":"hello"}""",
            )
            val ut = awaitItem() as RealtimeEvent.UserTranscript
            assertEquals("i1", ut.itemId)
            assertEquals("hello", ut.text)
            cancelAndIgnoreRemainingEvents()
        }

        session.close()
    }

    @Test
    fun errorEventNormalisesToGatewayError() = runTest {
        val gateway = FakeRealtimeGateway()
        val broker = RealtimeVoiceBroker(gateway)
        val session = broker.connect(
            model = "gpt-realtime",
            config = RealtimeVoiceConfig(),
            scope = backgroundScope,
        )
        val gs = gateway.openedSessions.single()

        session.events.test {
            gs.emit("""{"type":"error","error":{"message":"rate_limit"}}""")
            val ev = awaitItem() as RealtimeEvent.GatewayError
            assertEquals("rate_limit", ev.message)
            assertFalse(ev.recoverable)
            cancelAndIgnoreRemainingEvents()
        }

        session.close()
    }

    @Test
    fun voiceConfigFlowsThroughToGatewayOpenCall() = runTest {
        val gateway = FakeRealtimeGateway()
        val broker = RealtimeVoiceBroker(gateway)
        val config = RealtimeVoiceConfig(instructions = "be brisk", voice = "verse")
        broker.connect(model = "gpt-realtime", config = config, scope = backgroundScope)
        assertEquals("gpt-realtime", gateway.lastModel)
        assertEquals("be brisk", gateway.lastConfig?.instructions)
        assertEquals("verse", gateway.lastConfig?.voice)
        assertNotNull(gateway.openedSessions.single())
    }

    private companion object {
        const val MAX_DRAIN_LOOPS = 16
    }
}

private class RecordingTool(name: String, private val response: String) : LlmTool {
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = name,
        description = "test fixture",
        parameters = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {})
        },
    )

    var callCount: Int = 0
        private set

    override suspend fun execute(arguments: JsonObject): String {
        callCount += 1
        return response
    }
}
