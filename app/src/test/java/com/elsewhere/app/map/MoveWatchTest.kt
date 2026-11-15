package com.elsewhere.app.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MoveWatchTest {
    private val a = Cam(1.0, 1.0, 5f)
    private val b = Cam(1.1, 1.0, 5f)
    private val c = Cam(1.2, 1.0, 6f)

    @Test fun `the camera the app writes itself never starts a drag nor keeps the map unsettled`() {
        val w = MoveWatch()
        w.wrote(a); assertEquals(listOf(MapEvent.Move), w.saw(a, Cam(0.0, 0.0, 5f), gesture = false))
        w.wrote(b); assertEquals(listOf(MapEvent.Move), w.saw(b, a, gesture = false))
        assertNull(w.idle())
    }

    @Test fun `a hand on the map mid-follow fires DragStart once, and again after the map settles`() {
        val w = MoveWatch()
        w.wrote(a); w.saw(a, b, gesture = false)
        assertEquals(listOf(MapEvent.DragStart, MapEvent.Move, MapEvent.Zoom), w.saw(c, a, gesture = true))
        assertEquals(listOf(MapEvent.Move, MapEvent.Zoom), w.saw(b, c, gesture = true))
        assertEquals(MapEvent.Idle, w.idle()); assertNull(w.idle())
        assertEquals(listOf(MapEvent.DragStart, MapEvent.Move), w.saw(a, b, gesture = true))
    }

    @Test fun `a programmatic move between gestures separates them and an unchanged camera is nothing`() {
        val w = MoveWatch()
        assertEquals(emptyList<MapEvent>(), w.saw(a, a, gesture = true))
        assertEquals(listOf(MapEvent.DragStart, MapEvent.Move), w.saw(b, a, gesture = true))
        assertEquals(listOf(MapEvent.Move), w.saw(a, b, gesture = false))
        assertEquals(listOf(MapEvent.DragStart, MapEvent.Move), w.saw(b, a, gesture = true))
        // A round trip through the SDK's floats still counts as the app's own write.
        w.wrote(a); assertEquals(listOf(MapEvent.Move), w.saw(Cam(a.lat + 1e-9, a.lng + 1e-9, a.zoom), b, gesture = true))
    }
}
