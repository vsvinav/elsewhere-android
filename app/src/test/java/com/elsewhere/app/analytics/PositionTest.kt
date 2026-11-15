package com.elsewhere.app.analytics

import com.elsewhere.app.Fx
import com.elsewhere.app.Fx.act
import com.elsewhere.app.Fx.visit
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Path
import com.elsewhere.app.data.Pt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PositionTest {
    private val data = Fx.record(
        visits = listOf(
            visit(0, 10, lvl = 0, lng = 1.0, lat = 1.0),
            visit(2, 4, lvl = 1, lng = 9.0, lat = 9.0),
            visit(30, 40, lvl = 0, lng = 3.0, lat = 3.0),
        ),
        activities = listOf(act(10, 30, sLng = 1.0, sLat = 1.0, eLng = 3.0, eLat = 3.0)),
    )

    @Test fun `inside a top-level visit returns the place, ignoring nested visits`() {
        assertEquals(LatLng(1.0, 1.0), positionAt(data, 3))
    }

    @Test fun `inside an activity interpolates`() {
        assertEquals(LatLng(2.0, 2.0), positionAt(data, 20))
    }

    @Test fun `gap falls back to last visit, before everything is null`() {
        assertEquals(LatLng(3.0, 3.0), positionAt(data, 50))
        assertNull(positionAt(Fx.record(), 5))
    }

    @Test fun `distanceKm Boston to SF`() {
        val d = distanceKm(LatLng(42.3601, -71.0589), LatLng(37.7749, -122.4194))
        assertTrue(d > 4330); assertTrue(d < 4350)
    }

    @Test fun `whereaboutsAt reports movement mode`() {
        val d = Fx.record(
            visits = listOf(visit(0, 10, lvl = 0, lng = 1.0, lat = 1.0)),
            activities = listOf(act(10, 30, mode = "FLYING", sLng = 1.0, sLat = 1.0, eLng = 3.0, eLat = 3.0)),
        )
        assertEquals("STILL", whereaboutsAt(d, 5)?.mode)
        assertEquals("FLYING", whereaboutsAt(d, 20)?.mode)
    }

    @Test fun `inside a traced activity follows the GPS path and reports heading`() {
        val d = Fx.record(
            activities = listOf(act(0, 100, mode = "IN_PASSENGER_VEHICLE", sLng = 0.0, sLat = 0.0, eLng = 0.0, eLat = 2.0)),
            paths = listOf(Path(0, 100, listOf(Pt(0.0, 0.0, 0), Pt(1.0, 1.0, 50), Pt(2.0, 0.0, 100)))),
        )
        val w = whereaboutsAt(d, 25)!!
        assertEquals(0.5, w.lng, 5e-3); assertEquals(0.5, w.lat, 5e-3)
        assertEquals(45.0, w.heading, 0.5)
    }
}
