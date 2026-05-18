package com.mojentic.agents

/**
 * An agent in the event-driven agent system.
 *
 * Implementations receive an [Event], perform whatever work is appropriate,
 * and return a list of follow-up events. Returning an empty list is the
 * normal "no further events" outcome. Returning a [TerminateEvent] asks the
 * surrounding [AsyncDispatcher] to stop.
 *
 * The Kotlin port collapses the Python reference's sync/async pair
 * (`BaseAgent` / `BaseAsyncAgent`) into a single `suspend` surface — code
 * that does not need to suspend simply does not. The dispatcher always
 * `await`s the call.
 */
public interface Agent {
    public suspend fun receiveEvent(event: Event): List<Event>
}

/**
 * Convenience open base class for agents that do not need to suspend.
 *
 * Override [receiveEvent] with synchronous code; the `suspend` keyword is
 * still honoured by the dispatcher, but the body never actually suspends.
 */
public open class BaseAgent : Agent {
    override suspend fun receiveEvent(event: Event): List<Event> = emptyList()
}
