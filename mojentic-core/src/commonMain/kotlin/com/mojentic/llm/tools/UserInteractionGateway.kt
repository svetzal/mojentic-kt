package com.mojentic.llm.tools

/**
 * Abstraction over the user-interaction channel used by [AskUserTool] and
 * [TellUserTool].
 *
 * Gateway implementations are thin wrappers around the host channel
 * (stdin / stdout on JVM, system services on mobile, mocks in tests).
 * No business logic in implementations — they only carry I/O.
 */
public interface UserInteractionGateway {
    /**
     * Display [message] to the user without expecting a response.
     */
    public suspend fun tell(message: String)

    /**
     * Prompt the user with [request] and return their textual response.
     *
     * Implementations should block until the user has responded; in
     * suspend contexts (the typical case) the operation should suspend
     * rather than spin.
     */
    public suspend fun ask(request: String): String
}
