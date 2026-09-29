package com.mojentic.omlx

internal actual fun environmentVariable(name: String): String? = System.getenv(name)
