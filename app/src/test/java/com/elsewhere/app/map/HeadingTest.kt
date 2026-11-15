package com.elsewhere.app.map

import com.elsewhere.app.analytics.Segment
import com.elsewhere.app.analytics.SegmentKind
import com.elsewhere.app.data.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeadingTest {
    private fun leg(s: LatLng, e: LatLng, km: Double, kind: SegmentKind = SegmentKind.ACTIVITY) = Segment(kind, 0, 1, s, e, "FLYING", km)
    private val sfo = LatLng(37.619, -122.375); private val jfk = LatLng(40.64, -73.779)
    private val lon = LatLng(51.5074, -0.1278); private val syd = LatLng(-33.8688, 151.2093)

    @Test fun `a dot at rest and the fast-forward chevrons never turn`() {
        assertNull(stampHeading(leg(sfo, jfk, 4000.0), Stamp.STILL))
        assertNull(stampHeading(leg(sfo, jfk, 4000.0), Stamp.FF))
    }

    @Test fun `only an activity points somewhere, a visit or a gap stays upright`() {
        assertNull(stampHeading(leg(sfo, jfk, 4000.0, SegmentKind.VISIT), Stamp.FLY))
        assertNull(stampHeading(leg(sfo, jfk, 4000.0, SegmentKind.GAP), Stamp.FLY))
    }

    @Test fun `a short leg stays upright so the glyph reads, a long one points along the leg`() {
        assertEquals(15.0, POINT_KM, 0.0)
        assertNull(stampHeading(leg(LatLng(0.0, 0.0), LatLng(0.0, 0.1), 14.9), Stamp.DRIVE))
        assertEquals(90.0, stampHeading(leg(LatLng(0.0, 0.0), LatLng(0.0, 0.2), 15.0), Stamp.DRIVE)!!, 1e-5)
    }

    @Test fun `a long leg takes the initial great-circle bearing from its start to its end`() {
        assertEquals(69.8, stampHeading(leg(sfo, jfk, 4150.0), Stamp.FLY)!!, 0.05)
        assertEquals(60.7, stampHeading(leg(lon, syd, 17000.0), Stamp.FLY)!!, 0.05)
        assertEquals(180.0, stampHeading(leg(LatLng(20.0, 10.0), LatLng(0.0, 10.0), 2200.0), Stamp.TRANSIT)!!, 1e-5)
        assertEquals(0.0, stampHeading(leg(LatLng(0.0, 10.0), LatLng(20.0, 10.0), 2200.0), Stamp.FOOT)!!, 1e-5)
    }

    @Test fun `a leg that ends where it started has no direction`() {
        assertNull(stampHeading(leg(sfo, sfo, 100.0), Stamp.FLY))
    }

    @Test fun `the shown angle eases to the target along the shortest arc`() {
        val e = HeadingEase()
        assertEquals(350.0, e.at(350.0, 0), 0.0)
        val mid = e.at(10.0, 100)
        // 350 → 10 goes forward through 0, never back through 180.
        assertTrue(mid > 350 || mid < 10)
        assertNotEquals(350.0, mid)
        assertEquals(10.0, e.at(10.0, 500), 1e-6)
    }

    @Test fun `a mid-ease reading sits strictly between where it was and where it is going`() {
        val e = HeadingEase()
        e.at(0.0, 0)
        val mid = e.at(90.0, 175)
        assertTrue(mid > 0 && mid < 90)
        assertEquals(90.0, e.at(90.0, 600), 1e-6)
    }

    @Test fun `losing the heading eases the glyph back upright`() {
        val e = HeadingEase()
        e.at(0.0, 0)
        e.at(120.0, 1000)
        val mid = e.at(null, 1100)
        assertTrue(mid > 0 && mid < 120)
        assertEquals(0.0, e.at(null, 1500), 1e-6)
    }

    @Test fun `a stale ease snaps instead of sweeping from where it was long ago`() {
        val e = HeadingEase()
        e.at(0.0, 0)
        assertEquals(270.0, e.at(270.0, 5000), 0.0)
    }
}
