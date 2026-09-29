package com.mojentic.omlx

/**
 * Reads a process environment variable, or returns null where the platform
 * has no environment to configure the gateway from (Android and iOS).
 */
internal expect fun environmentVariable(name: String): String?
