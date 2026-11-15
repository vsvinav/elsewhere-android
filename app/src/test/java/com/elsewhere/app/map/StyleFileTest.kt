package com.elsewhere.app.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class StyleFileTest {
    private val dir = File("build/tmp/style-file-test")
    @BeforeEach fun clean() { dir.deleteRecursively(); dir.mkdirs() }

    @Test fun `the file is named by its content, so a new session gets a new URI`() {
        val a = styleFile(dir, """{"tiles":["a"]}""")
        val b = styleFile(dir, """{"tiles":["b"]}""")
        assertNotEquals(a.toURI(), b.toURI())
        assertEquals("""{"tiles":["a"]}""", a.readText())
        assertEquals("""{"tiles":["b"]}""", b.readText())
        assertTrue(a.name.startsWith("google-tiles-") && a.name.endsWith(".json"), a.name)
        assertEquals(a, styleFile(dir, """{"tiles":["a"]}"""))
        assertEquals(setOf(a.name, b.name), dir.list()!!.toSet()) // no temp files left behind
    }

    @Test fun `a style already on disk is known without writing, so the map can start on it`() {
        assertNull(cachedStyleFile(dir, """{"tiles":["a"]}"""))
        val a = styleFile(dir, """{"tiles":["a"]}""")
        assertEquals(a, cachedStyleFile(dir, """{"tiles":["a"]}"""))
        assertNull(cachedStyleFile(dir, """{"tiles":["b"]}"""))
    }
}
