package com.mojentic.agents

import com.mojentic.tracer.NullTracer
import com.mojentic.tracer.Tracer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

/**
 * Coroutine-driven event dispatcher.
 *
 * Events are pushed onto a FIFO queue with [dispatch], the dispatcher loop
 * picks them up in batches of [batchSize], routes each event through the
 * [Router], and feeds any produced events back onto the queue. A
 * [TerminateEvent] anywhere in the produced output stops the loop.
 *
 * Call [start] with a caller-owned [CoroutineScope] to launch the loop, and
 * [stop] (or cancel the scope) to shut it down. Tests can use
 * [waitForEmptyQueue] to settle the queue before asserting.
 */
@OptIn(ExperimentalUuidApi::class)
public class AsyncDispatcher(
    private val router: Router,
    private val tracer: Tracer = NullTracer,
    private val batchSize: Int = 5,
    private val pollDelayMs: Long = 10,
) {
    private val mutex = Mutex()
    private val queue: ArrayDeque<Event> = ArrayDeque()
    private val stopFlag = MutableStateFlow(false)
    private val inFlight = MutableStateFlow(0)
    private var job: Job? = null

    /** Launch the dispatch loop on the caller-supplied [scope] and return the [Job]. */
    public fun start(scope: CoroutineScope): Job {
        val j = scope.launch { dispatchLoop() }
        job = j
        return j
    }

    /** Set the stop flag and join the running dispatch loop. */
    public suspend fun stop() {
        stopFlag.value = true
        job?.cancelAndJoin()
    }

    /** Push [event] onto the queue. Assigns a fresh correlation id when missing. */
    public suspend fun dispatch(event: Event) {
        if (event.correlationId == null) {
            event.correlationId = Uuid.random().toString()
        }
        mutex.withLock { queue.addLast(event) }
    }

    /**
     * Suspend until the queue is empty AND no agent is currently being awaited,
     * or until [timeoutMs] elapses. Returns `true` if the queue drained,
     * `false` on timeout.
     */
    public suspend fun waitForEmptyQueue(timeoutMs: Long? = null): Boolean {
        val mark = TimeSource.Monotonic.markNow()
        while (true) {
            val empty = mutex.withLock { queue.isEmpty() } && inFlight.value == 0
            if (empty) return true
            if (timeoutMs != null && mark.elapsedNow().inWholeMilliseconds > timeoutMs) return false
            delay(pollDelayMs)
        }
    }

    /** Read the current queue depth — exposed for diagnostics and tests. */
    public suspend fun queueSize(): Int = mutex.withLock { queue.size }

    private suspend fun dispatchLoop() {
        while (!stopFlag.value) {
            var processed = false
            repeat(batchSize) {
                val event = mutex.withLock {
                    if (queue.isNotEmpty()) queue.removeFirst() else null
                } ?: return@repeat
                processed = true
                processEvent(event)
            }
            if (!processed) delay(pollDelayMs)
        }
    }

    private suspend fun processEvent(event: Event) {
        val agents = router.getAgents(event)
        logger.debug { "Processing ${event::class.simpleName} → ${agents.size} agent(s)" }
        val produced = mutableListOf<Event>()
        for (agent in agents) {
            tracer.recordAgentInteraction(
                fromAgent = event.source?.simpleName ?: "<unknown>",
                toAgent = agent::class.simpleName ?: "<anonymous>",
                eventType = event::class.simpleName ?: "<unknown>",
                eventId = event.correlationId,
                correlationId = event.correlationId,
            )
            inFlight.value += 1
            try {
                produced += agent.receiveEvent(event)
            } catch (cause: Throwable) {
                logger.warn(cause) { "Agent ${agent::class.simpleName} failed processing ${event::class.simpleName}" }
                throw cause
            } finally {
                inFlight.value -= 1
            }
        }
        for (next in produced) {
            if (next is TerminateEvent) stopFlag.value = true
            dispatch(next)
        }
    }
}
