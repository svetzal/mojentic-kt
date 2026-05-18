package com.mojentic.tracer

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass
import kotlin.time.Instant

/**
 * Append-only buffer of [TracerEvent]s plus a hot [SharedFlow] for live consumers.
 *
 * Reads return immutable snapshots. The append path uses an internal [Mutex]
 * so the buffer is safe to share across coroutines.
 *
 * Mirrors the Python reference `EventStore` (`tracer/event_store.py`).
 */
public class EventStore(
    private val replayCapacity: Int = 0,
) {
    private val mutex = Mutex()
    private val buffer: MutableList<TracerEvent> = mutableListOf()
    private val _events = MutableSharedFlow<TracerEvent>(
        replay = replayCapacity,
        extraBufferCapacity = DEFAULT_EXTRA_BUFFER,
    )

    /** Hot stream of every event recorded since this store was created. */
    public val events: SharedFlow<TracerEvent> get() = _events.asSharedFlow()

    /**
     * Store [event] in the buffer and emit it to live subscribers.
     *
     * The internal [SharedFlow] is created with extra buffer capacity, so
     * [MutableSharedFlow.tryEmit] usually succeeds without suspension; we
     * still fall back to a suspending emit when the extra buffer is full.
     */
    public suspend fun store(event: TracerEvent) {
        mutex.withLock { buffer += event }
        if (!_events.tryEmit(event)) _events.emit(event)
    }

    /**
     * Returns a snapshot of every event matching the supplied filters.
     *
     * Each filter is optional; if none is provided the entire buffer is
     * returned. Time bounds are inclusive.
     */
    public suspend fun getEvents(
        type: KClass<out TracerEvent>? = null,
        startTime: Instant? = null,
        endTime: Instant? = null,
        predicate: ((TracerEvent) -> Boolean)? = null,
    ): List<TracerEvent> = mutex.withLock {
        buffer.filter { event ->
            (type == null || type.isInstance(event)) &&
                (startTime == null || event.timestamp >= startTime) &&
                (endTime == null || event.timestamp <= endTime) &&
                (predicate == null || predicate(event))
        }
    }

    /**
     * Returns the last [n] events, optionally filtered to [type]. Order
     * matches insertion order (oldest first).
     */
    public suspend fun getLastN(n: Int, type: KClass<out TracerEvent>? = null): List<TracerEvent> {
        require(n >= 0) { "n must be non-negative" }
        return mutex.withLock {
            val filtered = if (type == null) buffer.toList() else buffer.filter { type.isInstance(it) }
            if (filtered.size <= n) filtered else filtered.subList(filtered.size - n, filtered.size).toList()
        }
    }

    /** Drop every event from the buffer. */
    public suspend fun clear() {
        mutex.withLock { buffer.clear() }
    }

    /** Current number of buffered events. */
    public suspend fun size(): Int = mutex.withLock { buffer.size }

    private companion object {
        const val DEFAULT_EXTRA_BUFFER = 64
    }
}
