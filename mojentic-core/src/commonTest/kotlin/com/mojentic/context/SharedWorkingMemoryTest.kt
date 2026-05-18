package com.mojentic.context

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class SharedWorkingMemoryTest {
    @Test
    fun mergeAddsKeys() = runTest {
        val memory = SharedWorkingMemory()
        memory.mergeToWorkingMemory(mapOf("name" to JsonPrimitive("ada")))
        memory.mergeToWorkingMemory(mapOf("city" to JsonPrimitive("london")))

        val snapshot = memory.getWorkingMemory()
        assertEquals(JsonPrimitive("ada"), snapshot["name"])
        assertEquals(JsonPrimitive("london"), snapshot["city"])
    }

    @Test
    fun mergeOverwritesExistingKey() = runTest {
        val memory = SharedWorkingMemory(mapOf("name" to JsonPrimitive("ada")))
        memory.mergeToWorkingMemory(mapOf("name" to JsonPrimitive("grace")))

        assertEquals(JsonPrimitive("grace"), memory.getWorkingMemory()["name"])
    }

    @Test
    fun getReturnsSnapshotNotLiveMap() = runTest {
        val memory = SharedWorkingMemory(mapOf("a" to JsonPrimitive(1)))
        val snapshot = memory.getWorkingMemory()
        memory.mergeToWorkingMemory(mapOf("b" to JsonPrimitive(2)))

        assertEquals(1, snapshot.size)
        assertNotSame(snapshot, memory.getWorkingMemory())
    }

    @Test
    fun replaceClearsExistingKeys() = runTest {
        val memory = SharedWorkingMemory(mapOf("a" to JsonPrimitive(1), "b" to JsonPrimitive(2)))
        memory.replaceWorkingMemory(mapOf("c" to JsonPrimitive(3)))

        val snapshot = memory.getWorkingMemory()
        assertEquals(1, snapshot.size)
        assertEquals(JsonPrimitive(3), snapshot["c"])
    }
}
