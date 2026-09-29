package com.mojentic.omlx

// iOS apps are not configured through process environment variables.
// Pass explicit values to the OmlxGateway constructor instead.
internal actual fun environmentVariable(name: String): String? = null
