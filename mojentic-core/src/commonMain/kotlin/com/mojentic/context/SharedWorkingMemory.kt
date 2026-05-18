package com.mojentic.context

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement

/**
 * A thread-safe map shared across agents in an event-driven system.
 *
 * The Kotlin port uses `Map<String, JsonElement>` so memory can be embedded in
 * structured-output schemas without losing type fidelity. Reads return a
 * snapshot copy; writes merge new keys into the held state behind a [Mutex].
 *
 * Mirrors the Python reference's `SharedWorkingMemory`.
 */
public class SharedWorkingMemory(initial: Map<String, JsonElement> = emptyMap()) {
    private val mutex = Mutex()
    private val state: MutableMap<String, JsonElement> = initial.toMutableMap()

    /** Return an immutable snapshot of the current memory. */
    public suspend fun getWorkingMemory(): Map<String, JsonElement> = mutex.withLock { state.toMap() }

    /** Merge [delta] into the held memory, overwriting matching keys. */
    public suspend fun mergeToWorkingMemory(delta: Map<String, JsonElement>) {
        mutex.withLock { state.putAll(delta) }
    }

    /** Replace the held memory entirely. */
    public suspend fun replaceWorkingMemory(replacement: Map<String, JsonElement>) {
        mutex.withLock {
            state.clear()
            state.putAll(replacement)
        }
    }
}
