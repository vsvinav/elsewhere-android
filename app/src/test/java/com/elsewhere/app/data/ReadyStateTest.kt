package com.elsewhere.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReadyStateTest {
    @Test fun `an import walks the four stages then finishes`() {
        val s = ReadyState()
        s.importing(0f); assertEquals(Ready("Reading file", 10), s.ready.value)
        s.importing(0.5f); assertEquals(Ready("Finding stops", 35), s.ready.value)
        s.importing(0.8f); assertEquals(Ready("Finding stops", 35), s.ready.value)
        s.importing(1f); assertEquals(Ready("Finding cities", 60), s.ready.value)
        s.deriving(); assertEquals(Ready("Working out trips", 85), s.ready.value)
        s.derived(); assertEquals(Ready("Working out trips", 100), s.ready.value)
    }

    @Test fun `a rebuild on its own starts half way`() {
        val s = ReadyState()
        s.deriving(); assertEquals(Ready("Working out trips", 50), s.ready.value)
        s.derived(); assertEquals(Ready("Working out trips", 100), s.ready.value)
        s.deriving(); assertEquals(Ready("Working out trips", 50), s.ready.value)
    }

    @Test fun `a failed import clears the bar`() {
        val s = ReadyState()
        s.importing(0.5f); s.clear(); assertNull(s.ready.value)
    }

    @Test fun `drawing paths finishes only when every lazy load has landed`() {
        val s = ReadyState()
        s.drawing(); s.drawing()
        assertEquals(Ready("Drawing paths", 0), s.ready.value)
        s.drawn(); assertEquals(Ready("Drawing paths", 0), s.ready.value)
        s.drawn(); assertEquals(Ready("Drawing paths", 100), s.ready.value)
    }
}
