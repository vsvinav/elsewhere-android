package com.elsewhere.app.map

import com.elsewhere.app.Fx
import com.elsewhere.app.Fx.H
import com.elsewhere.app.Fx.act
import com.elsewhere.app.Fx.visit
import com.elsewhere.app.data.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class FollowTest {
    private val a = LatLng(42.0, -71.0); private val b = LatLng(37.0, -122.0)
    private val data = Fx.record(
        visits = listOf(visit(0, 2 * H, lng = a.lng, lat = a.lat), visit(3 * H, 10 * H, lng = a.lng, lat = a.lat), visit(16 * H, 20 * H, lng = b.lng, lat = b.lat)),
        activities = listOf(
            act(2 * H, 3 * H, "WALKING", 5_000.0, a.lng, a.lat, a.lng, a.lat),
            act(10 * H, 16 * H, "FLYING", 4_300_000.0, a.lng, a.lat, b.lng, b.lat),
        ),
        range = 0L..20 * H,
    )

    @Test fun `the camera springs from where it was onto the subject and settles at town zoom on a long stay`() {
        val f = Follow()
        var cam = Cam(0.0, 0.0, 2f)
        var t = 0L
        repeat(600) { t += 16; cam = f.tick(data, now = 7 * H / 2, rate = 3600.0, speed = 1.0, dt = 0.016, tMs = t, w = 1080, h = 1600, current = cam) ?: cam }
        assertEquals(a.lat, cam.lat, 0.05); assertEquals(a.lng, cam.lng, 0.05)
        // The push-in is paced at 1.4 levels a second, so ten seconds is enough to reach the 14 town zoom.
        assertEquals(14f, cam.zoom, 0.3f)
    }

    @Test fun `a flight coming up opens the frame before it starts and the zoom cap falls with pace`() {
        val f = Follow()
        var cam = Cam(a.lat, a.lng, 14f)
        var t = 0L
        // Two real seconds before the flight at pace 1: the look-ahead has begun pulling out.
        repeat(300) { t += 16; cam = f.tick(data, now = 10 * H - 60, rate = 3600.0, speed = 1.0, dt = 0.016, tMs = t, w = 1080, h = 1600, current = cam) ?: cam }
        assertTrue(cam.zoom < 10f)
        val fast = Follow()
        var c2 = Cam(a.lat, a.lng, 14f)
        repeat(300) { t += 16; c2 = fast.tick(data, now = 5 * H, rate = 48 * 3600.0, speed = 32.0, dt = 0.016, tMs = t, w = 1080, h = 1600, current = c2) ?: c2 }
        assertTrue(c2.zoom <= zoomCapFor(48 * 3600.0).toFloat() + 0.1f)
    }

    @Test fun `a frame with no movement still reports the camera every half second`() {
        val f = Follow()
        var cam = Cam(a.lat, a.lng, 14f)
        var t = 0L
        repeat(2000) { t += 16; cam = f.tick(data, now = 7 * H / 2, rate = 3600.0, speed = 1.0, dt = 0.016, tMs = t, w = 1080, h = 1600, current = cam) ?: cam }
        assertNotNull(f.tick(data, now = 7 * H / 2, rate = 3600.0, speed = 1.0, dt = 0.016, tMs = t + 600, w = 1080, h = 1600, current = cam))
        assertTrue(abs(cam.lat - a.lat) < 1e-3)
    }
}
