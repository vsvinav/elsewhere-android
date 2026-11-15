package com.elsewhere.app.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoundsTest {
    @Test fun `the whole world fills a 256px square at zoom 0`() {
        val cam = camForBounds(Bounds(-85.05, -180.0, 85.05, 180.0), 256, 256, 0)
        assertEquals(0f, cam.zoom, 0.01f)
        assertEquals(0.0, cam.lat, 0.01); assertEquals(0.0, cam.lng, 1e-9)
    }

    @Test fun `each doubling of the viewport adds a level`() {
        val b = Bounds(37.0, -123.0, 38.0, -122.0)
        val z1 = camForBounds(b, 500, 500, 0).zoom
        val z2 = camForBounds(b, 1000, 1000, 0).zoom
        assertEquals(1f, z2 - z1, 0.001f)
    }

    @Test fun `padding tightens the frame and the centre is the projected middle`() {
        val b = Bounds(51.0, -1.0, 52.0, 1.0)
        val loose = camForBounds(b, 800, 600, 0); val tight = camForBounds(b, 800, 600, 100)
        assertTrue(tight.zoom < loose.zoom)
        assertEquals(0.0, loose.lng, 1e-9)
        assertTrue(loose.lat > 51.5 && loose.lat < 51.51)
    }

    @Test fun `a box across the antimeridian centres on the short way round`() {
        val cam = camForBounds(Bounds(-10.0, 170.0, 10.0, -170.0), 800, 800, 0)
        assertEquals(180.0, cam.lng, 1e-9)
        assertTrue(cam.zoom > 3f)
    }

    @Test fun `a single point caps at the max zoom`() {
        assertEquals(21f, camForBounds(Bounds(48.8, 2.3, 48.8, 2.3), 800, 800, 20).zoom)
    }
}
