package com.elsewhere.app.map

import com.elsewhere.app.Fx
import com.elsewhere.app.Fx.H
import com.elsewhere.app.Fx.act
import com.elsewhere.app.Fx.visit
import com.elsewhere.app.analytics.Segment
import com.elsewhere.app.analytics.SegmentKind
import com.elsewhere.app.data.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LookaheadTest {
    private val a = LatLng(42.0, -71.0); private val b = LatLng(37.0, -122.0)
    private fun v(start: Long, end: Long, p: LatLng = a) = visit(start, end, placeId = "p", lng = p.lng, lat = p.lat)
    private fun leg(start: Long, end: Long, s: LatLng, e: LatLng, m: Double, mode: String = "DRIVING") =
        act(start, end, mode, m, s.lng, s.lat, e.lng, e.lat)
    private val data = Fx.record(
        visits = listOf(v(0, 2 * H), v(3 * H, 10 * H), v(16 * H, 20 * H, b)),
        activities = listOf(leg(2 * H, 3 * H, a, a, 5_000.0), leg(10 * H, 16 * H, a, b, 4_300_000.0, "FLYING")),
        range = 0L..20 * H,
    )

    @Test fun `a flight a few segments ahead is found by screen time, steady whatever the current segment is`() {
        // From inside the first stay at 1x: ~1.5 s of stay left + 2.5 s walk + 2 s stay before the flight ≈ 6 s.
        assertEquals("FLYING", upcomingBig(data, 1 * H, 1.0, 8.0)?.seg?.mode)
        assertNull(upcomingBig(data, 1 * H, 1.0, 3.0))
        assertEquals("FLYING", upcomingBig(data, 1 * H, 4.0, 3.0)?.seg?.mode)
        assertNull(upcomingBig(data, 10 * H + 1, 1.0, 60.0))
        val x = upcomingBig(data, (9.5 * H).toLong(), 1.0, 8.0)!!; val y = upcomingBig(data, (9.9 * H).toLong(), 1.0, 8.0)!!
        assertTrue(x.inS > y.inS)
    }

    @Test fun `big legs keep screen time at any speed, small ones shrink`() {
        val flight = Segment(SegmentKind.ACTIVITY, 0, 6 * H, a, b, "FLYING", 4300.0)
        val hop = Segment(SegmentKind.ACTIVITY, 0, H, a, a, "DRIVING", 5.0)
        assertTrue(screenSeconds(flight, 32.0) > 4)
        assertTrue(screenSeconds(hop, 32.0) < 0.2)
        assertEquals(7.0, screenSeconds(flight, 1.0), 1e-9)
    }
}
