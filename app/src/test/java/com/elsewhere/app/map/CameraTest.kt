package com.elsewhere.app.map

import com.elsewhere.app.data.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.max

class CameraTest {
    @Test fun `spring settles on its target without overshooting past it noticeably`() {
        val s = Spring(0.0, 0.0)
        var maxX = 0.0
        repeat(600) { s.step(10.0, 1.0 / 60, 1.2); maxX = max(maxX, s.x) }
        assertEquals(10.0, s.x, 5e-3)
        assertTrue(maxX < 10.6)
    }

    @Test fun `zoom drops as speed rises and fitZoom frames a flight`() {
        assertTrue(zoomForKmh(3.0) > zoomForKmh(30.0))
        assertTrue(zoomForKmh(30.0) > zoomForKmh(800.0))
        val bos = LatLng(42.36, -71.06); val sfo = LatLng(37.62, -122.38)
        assertTrue(fitZoom(bos, sfo, 1440.0, 900.0) < 4.6)
        assertTrue(fitZoom(bos, LatLng(42.37, -71.07), 1440.0, 900.0) > 13)
    }

    @Test fun `faster replay calms the camera and lowers the zoom ceiling, zoom pulls out at once but pushes in slowly`() {
        val stroll = 3600.0; val dayPerSecond = 24 * 3600.0
        assertEquals(1.0, calmFor(stroll), 1e-9)
        assertTrue(calmFor(dayPerSecond) > 2)
        assertEquals(15.5, zoomCapFor(stroll), 1e-9)
        assertTrue(zoomCapFor(dayPerSecond) < 8)
        assertEquals(6.0, approachZoom(14.0, 6.0, 0.016, stroll), 1e-9)
        assertEquals(6.7, approachZoom(6.0, 14.0, 0.5, stroll), 1e-5)
        assertTrue(approachZoom(6.0, 14.0, 0.5, dayPerSecond) < 6.4)
    }

    @Test fun `dwell zoom frames a cluster of recent positions and goes to street level for one spot`() {
        val a = LatLng(42.36, -71.06)
        assertEquals(15.5, dwellZoom(listOf(a, a, a), 1440.0, 900.0), 1e-9)
        assertTrue(dwellZoom(listOf(a, LatLng(42.4, -71.2)), 1440.0, 900.0) < 13)
    }
}
