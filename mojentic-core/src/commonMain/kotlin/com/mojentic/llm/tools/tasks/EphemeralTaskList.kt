package com.mojentic.llm.tools.tasks

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * Lifecycle of an [EphemeralTask].
 *
 * Tasks always start in [Pending], may transition to [InProgress] via
 * `start`, and reach [Completed] via `complete`.
 */
@Serializable
public enum class TaskStatus {
    Pending,
    InProgress,
    Completed,
    ;

    public val wireValue: String
        get() = when (this) {
            Pending -> "pending"
            InProgress -> "in_progress"
            Completed -> "completed"
        }
}

/**
 * A single task on an [EphemeralTaskList].
 *
 * Tasks are deliberately ephemeral: the list lives inside the running
 * agent process and is not persisted.
 */
@Serializable
public data class EphemeralTask(
    val id: Int,
    val description: String,
    val status: TaskStatus = TaskStatus.Pending,
)

/**
 * In-memory task list with a small state machine. Safe to share across
 * coroutines — every public method takes a [Mutex] internally.
 *
 * Mirrors the Python reference `EphemeralTaskList`.
 */
public class EphemeralTaskList {
    private val mutex = Mutex()
    private val tasks: MutableList<EphemeralTask> = mutableListOf()
    private var nextId: Int = 1

    /** Append a new pending task at the tail. */
    public suspend fun append(description: String): EphemeralTask = mutex.withLock {
        val task = EphemeralTask(id = claimNextId(), description = description)
        tasks += task
        task
    }

    /** Prepend a new pending task at the head. */
    public suspend fun prepend(description: String): EphemeralTask = mutex.withLock {
        val task = EphemeralTask(id = claimNextId(), description = description)
        tasks.add(0, task)
        task
    }

    /**
     * Insert a new pending task immediately after [existingTaskId].
     *
     * @throws IllegalArgumentException if no task with that id exists.
     */
    public suspend fun insertAfter(existingTaskId: Int, description: String): EphemeralTask = mutex.withLock {
        val position = tasks.indexOfFirst { it.id == existingTaskId }
        require(position >= 0) { "No task with id '$existingTaskId' exists" }
        val task = EphemeralTask(id = claimNextId(), description = description)
        tasks.add(position + 1, task)
        task
    }

    /**
     * Transition the task from [TaskStatus.Pending] to [TaskStatus.InProgress].
     *
     * @throws IllegalArgumentException if no such task exists.
     * @throws IllegalStateException if the task is not currently pending.
     */
    public suspend fun start(id: Int): EphemeralTask = mutex.withLock {
        val position = requirePosition(id)
        val existing = tasks[position]
        check(existing.status == TaskStatus.Pending) {
            "Task '$id' cannot be started because it is not in PENDING status"
        }
        val updated = existing.copy(status = TaskStatus.InProgress)
        tasks[position] = updated
        updated
    }

    /**
     * Transition the task from [TaskStatus.InProgress] to
     * [TaskStatus.Completed].
     *
     * @throws IllegalArgumentException if no such task exists.
     * @throws IllegalStateException if the task is not currently in progress.
     */
    public suspend fun complete(id: Int): EphemeralTask = mutex.withLock {
        val position = requirePosition(id)
        val existing = tasks[position]
        check(existing.status == TaskStatus.InProgress) {
            "Task '$id' cannot be completed because it is not in IN_PROGRESS status"
        }
        val updated = existing.copy(status = TaskStatus.Completed)
        tasks[position] = updated
        updated
    }

    /** Snapshot of every task, in current order. */
    public suspend fun list(): List<EphemeralTask> = mutex.withLock { tasks.toList() }

    /**
     * Remove every task; returns the number of tasks that were cleared.
     */
    public suspend fun clear(): Int = mutex.withLock {
        val count = tasks.size
        tasks.clear()
        count
    }

    private fun claimNextId(): Int {
        val id = nextId
        nextId += 1
        return id
    }

    private fun requirePosition(id: Int): Int {
        val position = tasks.indexOfFirst { it.id == id }
        require(position >= 0) { "No task with id '$id' exists" }
        return position
    }
}
