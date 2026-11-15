package com.elsewhere.app.map

import com.elsewhere.app.analytics.Lens
import com.elsewhere.app.data.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

class ViewportTest {
    @Test fun `the view of a camera round-trips through camForBounds`() {
        val cam = Cam(42.36, -71.06, 11f)
        val b = viewBounds(cam, 1080, 1920)
        val back = camForBounds(b, 1080, 1920, 0)
        assertEquals(cam.lat, back.lat, 1e-6); assertEquals(cam.lng, back.lng, 1e-6); assertEquals(cam.zoom, back.zoom, 1e-3f)
        assertTrue(b.swLat < cam.lat && cam.lat < b.neLat); assertTrue(b.swLng < cam.lng && cam.lng < b.neLng)
    }

    @Test fun `a trail is kept when its box touches the view, across the date line too`() {
        val view = Bounds(40.0, -75.0, 45.0, -70.0)
        assertTrue(view.intersects(Bounds(44.0, -80.0, 50.0, -74.0)))
        assertFalse(view.intersects(Bounds(46.0, -75.0, 47.0, -70.0)))
        assertFalse(view.intersects(Bounds(40.0, -69.0, 45.0, -60.0)))
        // A view past 180° still catches a trail printed at -179°.
        val wrapped = viewBounds(Cam(0.0, 179.5, 6f), 1080, 1920)
        assertTrue(wrapped.neLng > 180.0)
        assertTrue(wrapped.intersects(Bounds(-1.0, -179.9, 1.0, -179.0)))
        assertFalse(wrapped.intersects(Bounds(-1.0, -150.0, 1.0, -140.0)))
        assertTrue(viewBounds(Cam(0.0, 0.0, 0f), 2000, 2000).intersects(Bounds(0.0, 100.0, 1.0, 101.0)))
    }

    @Test fun `a trail knows its box`() {
        val t = Trail(listOf(LatLng(1.0, 5.0), LatLng(-2.0, 7.0), LatLng(0.5, 3.0)), doubleArrayOf(0.0, 1.0, 2.0), Lens.DRIVE, false)
        assertEquals(Bounds(-2.0, 3.0, 1.0, 7.0), t.box)
    }
}
