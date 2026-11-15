package com.elsewhere.app.ui.tabs.flights

import com.elsewhere.app.analytics.Port
import com.elsewhere.app.data.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.hypot

class GlobeMathTest {
    private val view = GlobeView(cx = 100.0, cy = 100.0, r = 50.0)
    private val front = Rotation(0.0, 0.0)

    @Test fun `the centre of the hemisphere lands on the canvas centre`() {
        val p = project(0.0, 0.0, front, view)!!
        assertEquals(100.0, p[0], 1e-9); assertEquals(100.0, p[1], 1e-9)
        // Rotating to centre Tokyo puts Tokyo in the middle too.
        val t = project(139.7, 35.7, Rotation(139.7, 35.7), view)!!
        assertEquals(100.0, t[0], 1e-9); assertEquals(100.0, t[1], 1e-9)
    }

    @Test fun `a point ninety degrees away sits on the rim`() {
        val e = project(90.0, 0.0, front, view)!!
        assertEquals(150.0, e[0], 1e-9); assertEquals(100.0, e[1], 1e-9)
        val n = project(0.0, 90.0, front, view)!!
        assertEquals(100.0, n[0], 1e-9); assertEquals(50.0, n[1], 1e-9)
        assertEquals(50.0, hypot(e[0] - 100, e[1] - 100), 1e-9)
    }

    @Test fun `north is up and east is right`() {
        val ne = project(30.0, 30.0, front, view)!!
        assertTrue(ne[0] > 100 && ne[1] < 100)
    }

    @Test fun `the back hemisphere projects to nothing`() {
        assertNull(project(180.0, 0.0, front, view))
        assertNull(project(100.0, 0.0, front, view))
        assertNull(project(0.0, 0.0, Rotation(180.0, 0.0), view))
        assertNotNull(project(80.0, 0.0, front, view))
    }

    @Test fun `great circle midpoint of the equator quarter is forty five east`() {
        val pts = greatCircle(LatLng(0.0, 0.0), LatLng(0.0, 90.0), 64)
        assertEquals(65, pts.size)
        assertEquals(45.0, pts[32][0], 1e-9); assertEquals(0.0, pts[32][1], 1e-9)
        assertEquals(0.0, pts.first()[0], 1e-9); assertEquals(90.0, pts.last()[0], 1e-9)
    }

    @Test fun `a transpacific arc has monotone longitudes once unwrapped`() {
        val pts = greatCircle(LatLng(35.7, 139.7), LatLng(37.6, -122.4), 64)
        var prev = pts[0][0]; var acc = prev
        for (i in 1 until pts.size) {
            var l = pts[i][0]
            while (l - prev < -180) l += 360
            while (l - prev > 180) l -= 360
            assertTrue(l > prev, "lng went backwards at $i: $prev -> $l")
            acc = l; prev = l
        }
        assertEquals(360 - 122.4, acc, 1e-6)
        // The arc bows north, as the real route does.
        assertTrue(pts[32][1] > 45)
    }

    @Test fun `degenerate arcs do not blow up`() {
        val same = greatCircle(LatLng(10.0, 10.0), LatLng(10.0, 10.0), 8)
        assertEquals(9, same.size); same.forEach { assertEquals(10.0, it[0], 1e-9); assertEquals(10.0, it[1], 1e-9) }
        val anti = greatCircle(LatLng(0.0, 0.0), LatLng(0.0, 180.0), 16)
        assertEquals(17, anti.size)
        anti.forEach { assertTrue(it[0].isFinite() && it[1].isFinite()) }
        assertEquals(0.0, anti.first()[0], 1e-9); assertEquals(180.0, abs(anti.last()[0]), 1e-9)
    }

    @Test fun `clipping keeps a ring wholly in front as one closed run`() {
        val ring = doubleArrayOf(-10.0, -10.0, 10.0, -10.0, 10.0, 10.0, -10.0, 10.0, -10.0, -10.0)
        val runs = clipRing(ring, front, view)
        assertEquals(1, runs.size)
        assertEquals(ring.size, runs[0].size)
    }

    @Test fun `clipping drops a ring wholly behind`() {
        val ring = doubleArrayOf(170.0, -10.0, -170.0, -10.0, -170.0, 10.0, 170.0, 10.0, 170.0, -10.0)
        assertTrue(clipRing(ring, front, view).isEmpty())
    }

    @Test fun `a ring straddling the horizon is cut at the rim`() {
        // A band from 60°E to 120°E: its west half is visible, its east half behind the globe.
        val ring = doubleArrayOf(60.0, -10.0, 120.0, -10.0, 120.0, 10.0, 60.0, 10.0, 60.0, -10.0)
        val runs = clipRing(ring, front, view)
        assertEquals(1, runs.size)
        val run = runs[0]
        var onRim = 0
        var i = 0
        while (i + 1 < run.size) {
            val d = hypot(run[i] - 100.0, run[i + 1] - 100.0)
            assertTrue(d <= 50.0 + 1e-3, "point outside the disc: $d")
            if (abs(d - 50.0) < 1e-3) onRim++
            i += 2
        }
        assertTrue(onRim >= 2, "expected rim points, got $onRim")
    }

    @Test fun `the best centre faces the airports`() {
        val one = bestCentre(listOf(Port(LatLng(35.7, 139.7), "Tokyo", 3)))
        assertEquals(140.0, one.lng, 1e-9); assertEquals(35.0, one.lat, 1e-9)
        val pair = listOf(Port(LatLng(10.0, -20.0), "a", 1), Port(LatLng(-10.0, 20.0), "b", 1), Port(LatLng(10.0, 20.0), "c", 1))
        val two = bestCentre(pair)
        assertTrue(two.lng in -20.0..20.0 && two.lat in -10.0..10.0, "centre $two is not among the airports")
        pair.forEach { assertTrue(angleBetween(two, it.p.lng, it.p.lat) < 45, "centre $two too far from ${it.p}") }
        assertEquals(Rotation(0.0, 20.0), bestCentre(emptyList()))
    }

    @Test fun `the nearest arc within reach is picked, nothing otherwise`() {
        val arcs = listOf(
            listOf(doubleArrayOf(100.0, 100.0), doubleArrayOf(120.0, 100.0)),
            listOf(doubleArrayOf(100.0, 140.0), doubleArrayOf(120.0, 140.0)),
        )
        assertEquals(1, nearestArc(arcs, 110.0, 136.0, 24.0))
        assertEquals(0, nearestArc(arcs, 110.0, 110.0, 24.0))
        assertNull(nearestArc(arcs, 110.0, 200.0, 24.0))
    }
}
