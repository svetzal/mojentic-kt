package com.mojentic.errors

/**
 * Base type for all exceptions thrown by Mojentic library code at API boundaries.
 *
 * Specific failure modes extend this sealed class so callers can use `when` for
 * exhaustive matching at recovery points.
 */
public sealed class MojenticException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Raised when an LLM gateway call fails for any reason — transport, deserialisation,
 * provider rejection, or an unexpected response shape.
 */
public class LlmGatewayException(message: String, cause: Throwable? = null) : MojenticException(message, cause)

/**
 * Raised when the broker exhausts [com.mojentic.llm.CompletionConfig.maxToolIterations]
 * while recursively executing tool calls.
 */
public class MaxToolIterationsExceededException(message: String) : MojenticException(message)

/**
 * Raised when tool execution fails for a reason inherent to the tool itself
 * (bad arguments, downstream error). The broker serialises this into a tool-
 * result message and continues; callers using the tool runner directly receive
 * it on the failed outcome.
 */
public class ToolExecutionException(message: String, cause: Throwable? = null) : MojenticException(message, cause)

/**
 * Raised when a web-search gateway call fails for any reason — transport,
 * deserialisation, vendor rejection, or an unexpected response shape.
 */
public class WebSearchGatewayException(message: String, cause: Throwable? = null) : MojenticException(message, cause)
