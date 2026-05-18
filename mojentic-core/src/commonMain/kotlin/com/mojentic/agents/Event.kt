package com.mojentic.agents

import kotlin.reflect.KClass

/**
 * Domain event exchanged between agents through an [AsyncDispatcher].
 *
 * Events carry a [source] type hint (the agent class that emitted them) and a
 * [correlationId] linking events that belong to the same logical conversation.
 * The dispatcher assigns a fresh [correlationId] when one is not supplied, so
 * application code rarely needs to populate it directly.
 *
 * `Event` itself is open — subclass it with a `data class` (or plain class)
 * to carry domain-specific payloads.
 */
public open class Event(
    public val source: KClass<*>? = null,
    public var correlationId: String? = null,
)

/**
 * Sentinel event that terminates the surrounding [AsyncDispatcher] loop when
 * dispatched. Agents emit this to signal "all work is done — shut the dispatcher".
 */
public open class TerminateEvent(source: KClass<*>? = null) : Event(source = source)
