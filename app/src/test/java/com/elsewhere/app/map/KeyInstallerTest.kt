package com.elsewhere.app.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KeyInstallerTest {
    private val writes = mutableListOf<String>()
    @BeforeEach fun fresh() { KeyInstaller.reset(); writes.clear() }

    @Test fun `the first key is written once and is live`() {
        assertTrue(KeyInstaller.install("a", writes::add))
        assertTrue(KeyInstaller.install("a", writes::add))
        assertEquals(listOf("a"), writes)
    }

    @Test fun `a key can still change before the SDK starts`() {
        KeyInstaller.install("a", writes::add)
        assertTrue(KeyInstaller.install("b", writes::add))
        assertEquals(listOf("a", "b"), writes)
    }

    @Test fun `after the SDK starts only the key it read counts as live`() {
        KeyInstaller.install("a", writes::add); KeyInstaller.markStarted()
        assertFalse(KeyInstaller.install("b", writes::add))
        assertTrue(KeyInstaller.install("a", writes::add))
        assertEquals(listOf("a"), writes)
    }
}
