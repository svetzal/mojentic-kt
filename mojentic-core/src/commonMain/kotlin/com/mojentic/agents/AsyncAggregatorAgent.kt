package com.mojentic.agents

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.reflect.KClass

/**
 * Buffers events by [Event.correlationId] until every event-type in
 * [eventTypesNeeded] has been observed, then hands the collected list to
 * [processEvents].
 *
 * Useful when an agent must wait for fan-out work to complete before acting
 * (for example, gathering responses from multiple parallel sub-agents that
 * share a correlation id).
 *
 * Mirrors the Python reference's `AsyncAggregatorAgent`.
 */
public abstract class AsyncAggregatorAgent(
    private val eventTypesNeeded: List<KClass<out Event>>,
) : Agent {
    private val mutex = Mutex()
    private val pending: MutableMap<String, MutableList<Event>> = mutableMapOf()
    private val waiters: MutableMap<String, CompletableDeferred<List<Event>>> = mutableMapOf()

    override suspend fun receiveEvent(event: Event): List<Event> {
        val correlationId = event.correlationId ?: return emptyList()
        val (complete, snapshot) = mutex.withLock {
            val bucket = pending.getOrPut(correlationId) { mutableListOf() }
            bucket += event
            val captured = bucket.map { it::class }.toSet()
            val done = eventTypesNeeded.all { it in captured }
            if (done) {
                val drained = pending.remove(correlationId)!!.toList()
                waiters.remove(correlationId)?.complete(drained)
                true to drained
            } else {
                false to emptyList()
            }
        }
        return if (complete) processEvents(snapshot) else emptyList()
    }

    /**
     * Suspend until every needed event-type arrives for [correlationId], or
     * until [timeoutMs] elapses. Returns the captured events on success, or
     * whatever has been captured so far on timeout.
     */
    public suspend fun waitForEvents(correlationId: String, timeoutMs: Long? = null): List<Event> {
        val deferred = mutex.withLock {
            pending[correlationId]?.let { existing ->
                val captured = existing.map { it::class }.toSet()
                if (eventTypesNeeded.all { it in captured }) return existing.toList()
            }
            waiters.getOrPut(correlationId) { CompletableDeferred() }
        }
        return if (timeoutMs == null) {
            deferred.await()
        } else {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
                ?: mutex.withLock { pending[correlationId]?.toList() ?: emptyList() }
        }
    }

    /** Override to act on the assembled event list. Default returns no follow-up events. */
    public open suspend fun processEvents(events: List<Event>): List<Event> = emptyList()
}
