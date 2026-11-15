package com.elsewhere.app.map

import com.elsewhere.app.analytics.Segment
import com.elsewhere.app.analytics.SegmentKind
import com.elsewhere.app.data.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class FrameTest {
    private val a = LatLng(42.0, -71.0); private val b = LatLng(37.0, -122.0)
    private val flight = Segment(SegmentKind.ACTIVITY, 1000, 1000 + 6 * 3600, a, b, "FLYING", 4300.0)
    private val longStay = Segment(SegmentKind.VISIT, 0, 36000, a, a, "STILL", 0.0)
    private val blip = Segment(SegmentKind.VISIT, 0, 600, a, a, "STILL", 0.0)

    @Test fun `a leg is framed whole, with the centre drifting toward the dot`() {
        val start = frameFor(flight, null, a, 1000, 3600.0, 1200, 800)
        val late = frameFor(flight, null, b, 1000 + (5.9 * 3600).toLong(), 3600.0, 1200, 800)
        assertTrue(start.zoom < 6)
        assertEquals(start.zoom, late.zoom, 1e-9)
        assertTrue(abs(start.center.lng - (-96.5)) < 10)
        assertTrue(abs(late.center.lng - b.lng) < 3)
    }

    @Test fun `a long stay zooms to town, except its last beat before a flight`() {
        assertEquals(14.0, frameFor(longStay, flight, a, 1000, 60.0, 1200, 800).zoom, 1e-9)
        assertTrue(frameFor(longStay, flight, a, 35990, 60.0, 1200, 800).zoom < 8)
    }

    @Test fun `a short stop between legs keeps the wide frame`() {
        assertTrue(frameFor(blip, flight, a, 300, 600.0, 1200, 800).zoom < 8)
    }
}
