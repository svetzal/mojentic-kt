package com.mojentic.llm

import kotlinx.serialization.json.JsonElement

/**
 * Public stream event surfaced by [com.mojentic.llm.Broker.LlmBroker.stream].
 *
 * Cold `Flow<StreamEvent>` returned by the broker emits these in arrival order.
 * The broker handles tool execution internally; a tool round-trip surfaces to
 * callers as a [ToolCall] / [ToolResult] pair followed by further [TextChunk]s
 * from the recursive completion.
 */
public sealed interface StreamEvent {
    /** A chunk of assistant text content as it arrives. */
    public data class TextChunk(val text: String) : StreamEvent

    /** A chunk of reasoning / thinking trace, when the provider streams it. */
    public data class ThinkingChunk(val text: String) : StreamEvent

    /** A tool call the broker is about to dispatch. */
    public data class ToolCall(val call: LlmToolCall) : StreamEvent

    /** The serialised result of a tool call the broker dispatched. */
    public data class ToolResult(val call: LlmToolCall, val result: String, val isError: Boolean) : StreamEvent
}

/**
 * Low-level stream event emitted by [com.mojentic.llm.gateway.LlmGateway.stream].
 *
 * Gateways translate provider-specific stream payloads into this shape. The
 * broker accumulates these into a single response and orchestrates tool
 * execution before surfacing public [StreamEvent]s to callers.
 */
public sealed interface GatewayStreamEvent {
    public data class Content(val text: String) : GatewayStreamEvent

    public data class Thinking(val text: String) : GatewayStreamEvent

    public data class ToolCalls(val calls: List<LlmToolCall>) : GatewayStreamEvent

    /** Raw provider payload escape hatch, for adapters that need it. */
    public data class Raw(val payload: JsonElement) : GatewayStreamEvent
}
