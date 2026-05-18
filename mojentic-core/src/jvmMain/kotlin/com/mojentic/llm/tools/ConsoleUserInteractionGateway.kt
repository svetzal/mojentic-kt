package com.mojentic.llm.tools

/**
 * [UserInteractionGateway] backed by the host console (stdin / stdout).
 *
 * JVM-only — `readlnOrNull()` is multiplatform but stdin / stdout are
 * meaningful on JVM/Android-host CLI runs. iOS / Native consumers
 * inject their own platform-appropriate gateway.
 *
 * The implementation is intentionally trivial: print on `tell`, prompt
 * and read a line on `ask`. Both calls block the current thread; that
 * is acceptable for CLI examples but consumers running in non-blocking
 * environments should inject their own gateway.
 */
public class ConsoleUserInteractionGateway : UserInteractionGateway {
    override suspend fun tell(message: String) {
        println("\n\nMESSAGE FROM ASSISTANT:\n$message\n")
    }

    override suspend fun ask(request: String): String {
        println("\n\nI NEED YOUR HELP!\n$request")
        print("Your response: ")
        return readlnOrNull().orEmpty()
    }
}
