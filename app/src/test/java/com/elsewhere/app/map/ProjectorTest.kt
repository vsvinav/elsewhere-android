package com.elsewhere.app.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

class ProjectorTest {
    private val cam = Cam(48.8566, 2.3522, 12.5f)

    @Test fun `the camera's target sits in the middle of the viewport`() {
        val p = Projector(cam, 1080, 1920)
        val o = p.at(cam.lat, cam.lng)
        assertEquals(540f, o.x, 1e-3f); assertEquals(960f, o.y, 1e-3f)
    }

    @Test fun `the view's corners land on the corners of the screen`() {
        val w = 1080; val h = 1920
        val b = viewBounds(cam, w, h)
        val p = Projector(cam, w, h)
        val sw = p.at(b.swLat, b.swLng); val ne = p.at(b.neLat, b.neLng)
        assertEquals(0f, sw.x, 1e-2f); assertEquals(h.toFloat(), sw.y, 1e-2f)
        assertEquals(w.toFloat(), ne.x, 1e-2f); assertEquals(0f, ne.y, 1e-2f)
    }

    @Test fun `a degree of longitude is a fixed share of the 256 by 2 to the zoom world`() {
        for (zoom in listOf(3f, 10f, 15.5f)) {
            val p = Projector(Cam(0.0, 0.0, zoom), 1000, 1000)
            val o = p.at(0.0, 1.0)
            assertEquals(500 + 256 * 2.0.pow(zoom.toDouble()) / 360, o.x.toDouble(), 1e-2)
            assertEquals(500.0, o.y.toDouble(), 1e-3)
        }
    }

    @Test fun `north is up and east is right`() {
        val p = Projector(cam, 1000, 1000)
        val north = p.at(cam.lat + 0.01, cam.lng); val east = p.at(cam.lat, cam.lng + 0.01)
        assertTrue(north.y < 500f); assertEquals(500f, north.x, 1e-3f)
        assertTrue(east.x > 500f); assertEquals(500f, east.y, 1e-3f)
    }

    @Test fun `density scales the world, since a tile is 256 logical px on both SDKs`() {
        val one = Projector(Cam(0.0, 0.0, 4f), 1000, 1000)
        val three = Projector(Cam(0.0, 0.0, 4f), 1000, 1000, density = 3f)
        assertEquals((one.at(0.0, 10.0).x - 500) * 3, three.at(0.0, 10.0).x - 500, 1e-3f)
    }

    @Test fun `a point across the date line takes the short way round`() {
        val p = Projector(Cam(0.0, 179.0, 5f), 1000, 1000)
        val o = p.at(0.0, -179.0)
        assertEquals(500 + 2 * 256 * 2.0.pow(5.0) / 360, o.x.toDouble(), 1e-3)
    }

    @Test fun `the bounds it reports match viewBounds in logical pixels`() {
        val p = Projector(cam, 1080, 1920, density = 2f)
        assertEquals(viewBounds(cam, 540, 960), p.bounds)
    }
}
