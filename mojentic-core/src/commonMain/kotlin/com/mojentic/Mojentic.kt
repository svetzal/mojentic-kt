package com.mojentic

/**
 * Mojentic — Kotlin Multiplatform LLM integration framework.
 *
 * This is the Phase 0 skeleton. See `mojentic-unify/KOTLIN.md` for the full plan and roadmap.
 */
public object Mojentic {
    public const val VERSION: String = "0.0.1-SNAPSHOT"

    public fun greet(name: String = "world"): String = "Hello, $name, from Mojentic-KT $VERSION"
}
