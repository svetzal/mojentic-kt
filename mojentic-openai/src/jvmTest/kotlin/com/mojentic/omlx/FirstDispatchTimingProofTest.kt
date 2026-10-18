package com.mojentic.omlx

import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.CompletionStreamEvent
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.recovery.RecoveryPolicy
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Baseline-compatible consumer probe: in-process transport, first dispatch, retry admission and mixed-frame precedence. */
class FirstDispatchTimingProofTest {
    @Test
    fun delayedCollectionRetryAndMixedFrameUseOneDispatchClock(): Unit = runTest {
        val now = AtomicLong(0)
        val first = ScriptedReply(503, "busy", beforeReply = { now.addAndGet(10) })
        val second = ScriptedReply(
            200,
            "data: {\"model\":\"keepalive\",\"choices\":[]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"\",\"reasoning_content\":\"\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"visible\",\"reasoning_content\":\"private\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n",
            beforeReply = { now.addAndGet(20) },
        )
        RecoveryScriptedTransport(listOf(first, second)).use { server ->
            val gateway = OmlxGateway(host = server.url, apiKey = "test", engine = server.engine)
            try {
                val policy = RecoveryPolicy(
                    maxAttempts = 2,
                    admission = {
                        now.addAndGet(7)
                        true
                    },
                    monotonicClock = { now.get() },
                    sleep = { now.addAndGet(40) },
                    jitter = { 0 },
                )
                val stream = gateway.streamEvents("test", listOf(LlmMessage.user("hello")), CompletionConfig(recovery = policy))
                now.set(1000)
                assertEquals(0, server.requests.size)
                val events = stream.toList()
                assertEquals(CompletionStreamEvent.Content("visible"), events.first())
                val evidence = assertIs<CompletionStreamEvent.Completed>(events.last()).evidence
                assertEquals(JsonPrimitive(77), evidence.metadata?.get("time_to_first_token_ms"))
                assertEquals(JsonPrimitive("thinking"), evidence.metadata?.get("first_token_kind"))
                assertEquals(JsonPrimitive(77), evidence.metadata?.get("request_duration_ms"))
                assertEquals(2, server.requests.size)
                assertContentEquals(server.requests[0], server.requests[1])
            } finally {
                gateway.close()
            }
        }
    }
}
