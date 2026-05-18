package com.mojentic.agents

import kotlin.reflect.KClass

/**
 * Maps event types to the agents that should receive them.
 *
 * The router is populated up-front when wiring an agent system and is
 * effectively read-only from then on — it is not thread-safe across
 * concurrent `addRoute` calls. Looking up agents during dispatch is
 * concurrency-safe because the underlying map is no longer mutated.
 */
public class Router(routes: Map<KClass<out Event>, List<Agent>> = emptyMap()) {
    private val routes: MutableMap<KClass<out Event>, MutableList<Agent>> =
        routes.mapValues { it.value.toMutableList() }.toMutableMap()

    public fun addRoute(eventType: KClass<out Event>, agent: Agent) {
        routes.getOrPut(eventType) { mutableListOf() }.add(agent)
    }

    public fun getAgents(event: Event): List<Agent> = routes[event::class].orEmpty()
}
