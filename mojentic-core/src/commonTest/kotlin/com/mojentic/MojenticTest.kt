package com.mojentic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MojenticTest {
    @Test
    fun versionIsExposed() {
        assertTrue(Mojentic.VERSION.isNotBlank(), "VERSION should be non-blank")
    }

    @Test
    fun greetUsesDefaultName() {
        assertEquals("Hello, world, from Mojentic-KT ${Mojentic.VERSION}", Mojentic.greet())
    }

    @Test
    fun greetUsesProvidedName() {
        assertEquals("Hello, Stacey, from Mojentic-KT ${Mojentic.VERSION}", Mojentic.greet("Stacey"))
    }
}
