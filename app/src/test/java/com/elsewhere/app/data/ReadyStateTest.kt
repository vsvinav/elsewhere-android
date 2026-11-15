package com.elsewhere.app.data

import com.elsewhere.app.ingest.Progress
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

    @Test fun `the shell bar carries on into naming once the local work is done`() {
        val naming = Progress(3, 10)
        // Local work still going: it wins, whatever the lookups are up to.
        assertEquals(Ready("Finding cities", 60), shellReady(Ready("Finding cities", 60), naming))
        // Derive done but names still landing: the bar stays, counting them in.
        assertEquals(Ready("Naming places 3/10", 30), shellReady(Ready("Working out trips", 100), naming))
        assertEquals(Ready("Naming places 0/4", 0), shellReady(null, Progress(0, 4)))
        // Nothing to name: unchanged.
        assertEquals(Ready("Working out trips", 100), shellReady(Ready("Working out trips", 100), null))
        assertNull(shellReady(null, null))
        assertNull(shellReady(null, Progress(0, 0)))
        // Every name landed: 100, so the bar lifts.
        assertEquals(Ready("Naming places 10/10", 100), shellReady(Ready("Working out trips", 100), Progress(10, 10)))
    }

    @Test fun `when a naming burst clears, the bar finishes on the names rather than bringing back the old done bar`() {
        val trips = Ready("Working out trips", 100)
        // The lookups report 10/10 then null; a collector may see only the null.
        assertEquals(Ready("Naming places 10/10", 100), shellReady(trips, null, prev = Ready("Naming places 10/10", 100)))
        assertEquals(Ready("Naming places 10/10", 100), shellReady(trips, null, prev = Ready("Naming places 3/10", 30)))
        assertEquals(Ready("Naming places 4/4", 100), shellReady(null, null, prev = Ready("Naming places 0/4", 0)))
        // A finished local stage is still shown when it really is new.
        assertEquals(Ready("Drawing paths", 100), shellReady(Ready("Drawing paths", 100), null, prev = Ready("Drawing paths", 0)))
        assertEquals(trips, shellReady(trips, null, prev = Ready("Working out trips", 50)))
        // Local work going again after naming: it wins, as ever.
        assertEquals(Ready("Drawing paths", 0), shellReady(Ready("Drawing paths", 0), null, prev = Ready("Naming places 4/4", 100)))
    }

    @Test fun `drawing paths finishes only when every lazy load has landed`() {
        val s = ReadyState()
        s.drawing(); s.drawing()
        assertEquals(Ready("Drawing paths", 0), s.ready.value)
        s.drawn(); assertEquals(Ready("Drawing paths", 0), s.ready.value)
        s.drawn(); assertEquals(Ready("Drawing paths", 100), s.ready.value)
    }
}
