package com.mojentic.agents

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class RouterPingEvent : Event()
private class RouterPongEvent : Event()

private class RouterStubAgent : Agent {
    val received: MutableList<Event> = mutableListOf()
    override suspend fun receiveEvent(event: Event): List<Event> {
        received += event
        return emptyList()
    }
}

class RouterTest {
    @Test
    fun returnsRegisteredAgentsForMatchingEventType() = runTest {
        val agent = RouterStubAgent()
        val router = Router()
        router.addRoute(RouterPingEvent::class, agent)

        val found = router.getAgents(RouterPingEvent())

        assertEquals(listOf(agent), found)
    }

    @Test
    fun returnsEmptyListWhenNoRouteRegistered() = runTest {
        val router = Router()

        assertTrue(router.getAgents(RouterPongEvent()).isEmpty())
    }

    @Test
    fun supportsMultipleAgentsPerEventType() = runTest {
        val a = RouterStubAgent()
        val b = RouterStubAgent()
        val router = Router()
        router.addRoute(RouterPingEvent::class, a)
        router.addRoute(RouterPingEvent::class, b)

        assertEquals(listOf(a, b), router.getAgents(RouterPingEvent()))
    }
}
