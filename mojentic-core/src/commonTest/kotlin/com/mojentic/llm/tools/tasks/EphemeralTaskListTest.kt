package com.mojentic.llm.tools.tasks

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EphemeralTaskListTest {
    @Test
    fun appendAddsTaskAtTail() = runTest {
        val list = EphemeralTaskList()

        list.append("first")
        val second = list.append("second")

        val all = list.list()
        assertEquals(2, all.size)
        assertEquals("second", all.last().description)
        assertEquals(2, second.id)
        assertEquals(TaskStatus.Pending, second.status)
    }

    @Test
    fun prependAddsTaskAtHead() = runTest {
        val list = EphemeralTaskList()
        list.append("existing")

        val head = list.prepend("new head")

        val all = list.list()
        assertEquals("new head", all.first().description)
        assertEquals(head.id, all.first().id)
    }

    @Test
    fun insertAfterPlacesTaskInRightPosition() = runTest {
        val list = EphemeralTaskList()
        val first = list.append("a")
        list.append("c")

        list.insertAfter(first.id, "b")

        assertEquals(listOf("a", "b", "c"), list.list().map { it.description })
    }

    @Test
    fun insertAfterUnknownIdThrows() = runTest {
        val list = EphemeralTaskList()

        assertFailsWith<IllegalArgumentException> { list.insertAfter(99, "boom") }
    }

    @Test
    fun startTransitionsPendingToInProgress() = runTest {
        val list = EphemeralTaskList()
        val task = list.append("work")

        val started = list.start(task.id)

        assertEquals(TaskStatus.InProgress, started.status)
        assertEquals(TaskStatus.InProgress, list.list().single().status)
    }

    @Test
    fun startTwiceThrows() = runTest {
        val list = EphemeralTaskList()
        val task = list.append("work")
        list.start(task.id)

        assertFailsWith<IllegalStateException> { list.start(task.id) }
    }

    @Test
    fun completeRequiresInProgressStatus() = runTest {
        val list = EphemeralTaskList()
        val task = list.append("work")

        assertFailsWith<IllegalStateException> { list.complete(task.id) }
    }

    @Test
    fun completeTransitionsInProgressToCompleted() = runTest {
        val list = EphemeralTaskList()
        val task = list.append("work")
        list.start(task.id)

        val done = list.complete(task.id)

        assertEquals(TaskStatus.Completed, done.status)
    }

    @Test
    fun clearReturnsCountAndEmptiesList() = runTest {
        val list = EphemeralTaskList()
        list.append("a")
        list.append("b")

        val cleared = list.clear()

        assertEquals(2, cleared)
        assertTrue(list.list().isEmpty())
    }

    @Test
    fun idsAreMonotonicAcrossOperations() = runTest {
        val list = EphemeralTaskList()
        val a = list.append("a")
        val b = list.prepend("b")
        val c = list.insertAfter(a.id, "c")

        assertEquals(1, a.id)
        assertEquals(2, b.id)
        assertEquals(3, c.id)
    }
}
