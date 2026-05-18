package com.mojentic

import kotlin.test.Test
import kotlin.test.assertTrue

class MojenticTest {
    @Test
    fun versionIsExposed() {
        assertTrue(Mojentic.VERSION.isNotBlank(), "VERSION should be non-blank")
    }
}
