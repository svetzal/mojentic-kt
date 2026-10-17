package com.mojentic.openai

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.CompletionEvidence
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.GatewayStreamEvent
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.recovery.RecoveryPolicy
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClientStreamTimingTest {
    @Test
    fun delayedCollectionAndAdmittedRetryKeepFirstDispatchClockWithThinkingPrecedence() = runTest {
        var requests = 0
        val engine = MockEngine(
            MockEngineConfig().apply {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler {
                    requests++
                    delay(10)
                    if (requests == 1) {
                        respond("busy", HttpStatusCode.ServiceUnavailable)
                    } else {
                        respond(
                            ": ping\n\n" +
                                "data: {\"choices\":[{\"delta\":{}}]}\n\n" +
                                "data: {\"choices\":[{\"delta\":{\"content\":\"hi\",\"reasoning_content\":\"think\"}}]}\n\n" +
                                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n",
                        )
                    }
                }
            },
        )
        val gateway = OpenAIGateway("test", engine = engine)
        try {
            val stream = gateway.streamEvents(
                "test",
                listOf(LlmMessage.user("hello")),
                CompletionConfig(
                    recovery = RecoveryPolicy(
                        maxAttempts = 2,
                        admission = { true },
                        monotonicClock = { testScheduler.currentTime },
                        sleep = { delay(40) },
                        jitter = { 0 },
                    ),
                ),
            )
            delay(1000)
            assertEquals(0, requests)
            val completed = stream.toList().last() as CompletionStreamEvent.Completed
            assertEquals(JsonPrimitive(60), completed.evidence.metadata?.get("time_to_first_token_ms"))
            assertEquals(JsonPrimitive("thinking"), completed.evidence.metadata?.get("first_token_kind"))
            assertEquals(JsonPrimitive(60), completed.evidence.metadata?.get("request_duration_ms"))
            assertEquals(2, requests)
        } finally {
            gateway.close()
        }
    }

    @Test
    fun argumentFragmentStartsClockBeforeCompletedToolOnBothPaths() = runTest {
        for (recovery in listOf(false, true)) {
            val body = ByteChannel(autoFlush = true)
            val dispatched = CompletableDeferred<Unit>()
            val engine = MockEngine(
                MockEngineConfig().apply {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler {
                        delay(10)
                        dispatched.complete(Unit)
                        respond(body)
                    }
                },
            )
            val gateway = OpenAIGateway("test", engine = engine)
            val writer = launch {
                dispatched.await()
                delay(5)
                body.writeStringUtf8(
                    "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"t1\",\"function" +
                        "\":{\"name\":\"lookup\",\"arguments\":\"\"}}]}}]}\n\n",
                )
                delay(10)
                body.writeStringUtf8(
                    "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"argument" +
                        "s\":\"{\"}}]}}]}\n\n",
                )
                delay(20)
                body.writeStringUtf8(
                    "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"argument" +
                        "s\":\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}\n\ndata: [DONE]\n\n",
                )
                body.close()
            }
            try {
                val clock = object : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
                    override fun read(): Long = testScheduler.currentTime
                }
                val policy = if (recovery) RecoveryPolicy(monotonicClock = { testScheduler.currentTime }) else null
                val events = gateway.stream(
                    "test",
                    listOf(LlmMessage.user("hello")),
                    config = CompletionConfig(recovery = policy, streamTimeSource = clock),
                ).toList()
                assertEquals(1, events.filterIsInstance<GatewayStreamEvent.ToolCalls>().size)
                val evidence = assertIs<GatewayStreamEvent.Completed>(events.last()).evidence
                assertTiming(evidence, "tool_call", 25, 45)
            } finally {
                writer.cancel()
                gateway.close()
            }
        }
    }

    @Test
    fun terminalFailureAfterContentKeepsFirstTokenTimeAndFullDuration() = runTest {
        for (recovery in listOf(false, true)) {
            val body = ByteChannel(autoFlush = true)
            val dispatched = CompletableDeferred<Unit>()
            val engine = MockEngine(
                MockEngineConfig().apply {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler {
                        delay(10)
                        dispatched.complete(Unit)
                        respond(body)
                    }
                },
            )
            val gateway = OpenAIGateway("test", engine = engine)
            val writer = launch {
                dispatched.await()
                delay(15)
                body.writeStringUtf8(": ping\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n")
                delay(20)
                body.writeStringUtf8(
                    "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"late thinking\"}}]}\n\n" +
                        "data: {not json\n\n",
                )
                body.close()
            }
            try {
                val clock = object : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
                    override fun read(): Long = testScheduler.currentTime
                }
                val policy = if (recovery) RecoveryPolicy(monotonicClock = { testScheduler.currentTime }) else null
                val events = gateway.streamEvents(
                    "test",
                    listOf(LlmMessage.user("hello")),
                    CompletionConfig(recovery = policy, streamTimeSource = clock),
                ).toList()
                assertEquals(listOf(CompletionStreamEvent.Content("hello")), events.filterIsInstance<CompletionStreamEvent.Content>())
                assertTiming(requireNotNull(assertIs<CompletionStreamEvent.Error>(events.last()).evidence), "content", 25, 45)
            } finally {
                writer.cancel()
                gateway.close()
            }
        }
    }

    private fun assertTiming(evidence: CompletionEvidence, kind: String, first: Int, duration: Int) {
        assertEquals(JsonPrimitive(first), evidence.metadata?.get("time_to_first_token_ms"))
        assertEquals(JsonPrimitive(kind), evidence.metadata?.get("first_token_kind"))
        assertEquals(JsonPrimitive(duration), evidence.metadata?.get("request_duration_ms"))
    }
}
