package com.elsewhere.app.map

import com.elsewhere.app.Fx
import com.elsewhere.app.Fx.H
import com.elsewhere.app.Fx.act
import com.elsewhere.app.Fx.visit
import com.elsewhere.app.analytics.Lens
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Path
import com.elsewhere.app.data.Pt
import com.elsewhere.app.data.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrailTest {
    @Test fun `simplify keeps the ends and the corner, drops the jitter`() {
        val pts = listOf(LatLng(0.0, 0.0), LatLng(0.0001, 1.0), LatLng(0.0, 2.0), LatLng(1.0, 3.0))
        assertEquals(listOf(0, 2, 3), simplifyIdx(pts, 0.01).toList())
        assertEquals(listOf(0, 1), simplifyIdx(pts.take(2), 0.01).toList())
    }

    @Test fun `wake length is clamped and the stamp holds before changing`() {
        assertEquals(3 * 3600.0, wakeLength(60.0), 1e-9)
        assertEquals(30 * 86400.0, wakeLength(1e9), 1e-9)
        val hold = StampHold()
        assertEquals(Stamp.STILL, hold.kind(Stamp.FLY, 3600.0, bigLeg = true, tMs = 0))
        assertEquals(Stamp.STILL, hold.kind(Stamp.FLY, 3600.0, bigLeg = true, tMs = 300))
        assertEquals(Stamp.FLY, hold.kind(Stamp.FLY, 3600.0, bigLeg = true, tMs = 400))
        // Fast replay over a small leg shows chevrons, but a big leg keeps its vehicle.
        hold.kind(Stamp.DRIVE, 48 * 3600.0, bigLeg = false, tMs = 1000)
        assertEquals(Stamp.FF, hold.kind(Stamp.DRIVE, 48 * 3600.0, bigLeg = false, tMs = 1400))
        hold.kind(Stamp.FLY, 48 * 3600.0, bigLeg = true, tMs = 2000)
        assertEquals(Stamp.FLY, hold.kind(Stamp.FLY, 48 * 3600.0, bigLeg = true, tMs = 2400))
    }

    @Test fun `active trails and the trail head follow the record`() {
        val data = Fx.record(
            activities = listOf(act(0, H, "WALKING", 1000.0, 0.0, 0.0, 1.0, 0.0), act(2 * H, 3 * H, "FLYING", 500_000.0, 1.0, 0.0, 1.0, 1.0)),
        )
        val trails = ReplayTrails(data).all
        assertEquals(1, activeTrails(trails, 0.0, H / 2.0).size)
        assertEquals(2, activeTrails(trails, H / 2.0, 2.5 * H).size)
        val head = trailHead(trails, H / 2.0)!!
        assertEquals(0.5, head.lng, 1e-9); assertEquals(0.0, head.lat, 1e-9)
        assertNull(trailHead(trails, 1.5 * H))
    }

    @Test fun `a long leg still draws while a short one inside it is the latest to start`() {
        // A = [0, 100 h] encloses B = [10 h, 20 h]; a window at 50..60 h must still find A.
        val data = Fx.record(activities = listOf(act(0, 100 * H, "IN_PASSENGER_VEHICLE", 1000.0), act(10 * H, 20 * H, "WALKING", 100.0)))
        val trails = ReplayTrails(data).all
        assertEquals(listOf(0.0), activeTrails(trails, 50.0 * H, 60.0 * H).map { it.t0 })
        assertEquals(2, activeTrails(trails, 15.0 * H, 16.0 * H).size)
    }

    @Test fun `a traced leg is drawn once and an untraced flight gets a synthetic arc`() {
        val data = Fx.record(
            activities = listOf(act(0, 100, "IN_PASSENGER_VEHICLE", 1000.0, 0.0, 0.0, 2.0, 0.0), act(200, 300, "FLYING", 500_000.0, 0.0, 0.0, 5.0, 5.0)),
            paths = listOf(Path(0, 100, listOf(Pt(0.0, 0.0, 0), Pt(1.0, 1.0, 50), Pt(2.0, 0.0, 100)))),
        )
        val trails = ReplayTrails(data).all
        assertEquals(2, trails.size)
        assertEquals(listOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0), LatLng(0.0, 2.0)), trails[0].path); assertFalse(trails[0].guessed)
        assertTrue(trails[1].path.size > 2); assertFalse(trails[1].guessed)
        assertEquals(LatLng(0.0, 0.0), trails[1].path.first())
        assertEquals(5.0, trails[1].path.last().lat, 1e-9); assertEquals(5.0, trails[1].path.last().lng, 1e-9)
    }

    @Test fun `wake is sixteen screen seconds, jitter collapses, and zooming in tightens the tolerance`() {
        assertEquals(16 * 3600.0, wakeLength(3600.0), 1e-9)
        val rnd = java.util.Random(7)
        val jitter = List(50) { i -> LatLng((rnd.nextDouble() - 0.5) * 1e-5, i / 10.0) }
        assertEquals(listOf(0, 49), simplifyIdx(jitter, 1e-3).toList())
        assertTrue(degPerPx(14) < degPerPx(6) / 200)
    }

    @Test fun `a frame never thins on its own thread - it takes the nearest ready bucket until prepare has run`() {
        val rnd = java.util.Random(3)
        val pts = List(200) { i -> Pt(i / 100.0 + (rnd.nextDouble() - 0.5) * 1e-4, i / 100.0, i * 10L) }
        val data = Fx.record(
            activities = listOf(act(0, 2000, "WALKING", 3000.0, 0.0, 0.0, 2.0, 2.0)),
            paths = listOf(Path(0, 2000, pts)),
        )
        val trails = ReplayTrails(data)
        // Nothing ready: the raw trails stand in, and the lookup computes nothing.
        assertSame(trails.all, trails.simplified(Lens.ALL, 10.0))
        assertEquals(0, trails.computed)
        // One bucket ready: a zoom two levels off borrows it rather than thinning its own.
        trails.compute(Lens.ALL, 8)
        assertEquals(1, trails.computed)
        val z8 = trails.simplified(Lens.ALL, 8.0)
        assertNotSame(trails.all, z8); assertTrue(z8[0].path.size < 200)
        assertSame(z8, trails.simplified(Lens.ALL, 10.0))
        assertEquals(1, trails.computed)
        // Prepared: every lens and bucket is ready, so no lookup ever thins again.
        trails.prepare()
        val n = trails.computed
        assertEquals(Lens.entries.size * 15, n)
        for (lens in Lens.entries) for (z in 0..18) trails.simplified(lens, z.toDouble())
        assertEquals(n, trails.computed)
        assertTrue(trails.simplified(Lens.ALL, 16.0)[0].path.size > trails.simplified(Lens.ALL, 2.0)[0].path.size)
    }

    @Test fun `the trail head is found by binary search inside a long leg`() {
        val pts = List(1000) { i -> Pt(0.0, i / 1000.0, i.toLong()) }
        val data = Fx.record(activities = listOf(act(0, 1000, "WALKING", 1000.0, 0.0, 0.0, 1.0, 0.0)), paths = listOf(Path(0, 1000, pts)))
        val trails = ReplayTrails(data).all
        assertEquals(0.5005, trailHead(trails, 500.5)!!.lng, 1e-9)
        assertEquals(0.0, trailHead(trails, 0.0)!!.lng, 1e-9)
        assertEquals(1.0, trailHead(trails, 1000.0)!!.lng, 1e-9)
    }

    @Test fun `arrivals print once per visit, skip home, and peel off after the TTL`() {
        val data = Fx.record(
            visits = listOf(visit(0, 10, "home", id = 1), visit(20, 30, "cafe", id = 2), visit(40, 50, "cafe", id = 3)),
            places = mapOf("home" to Fx.place(role = Role.HOME), "cafe" to Fx.place(name = "Cafe")),
        )
        val a = Arrivals()
        a.at(data, 5, playing = true, nowMs = 0); assertTrue(a.tags.isEmpty())
        a.at(data, 25, playing = true, nowMs = 1000); assertEquals(listOf(1), a.tags.map { it.nth })
        a.at(data, 26, playing = true, nowMs = 1100); assertEquals(1, a.tags.size)
        a.at(data, 45, playing = false, nowMs = 2000); assertEquals(1, a.tags.size)
        a.at(data, 45, playing = true, nowMs = 2000); assertEquals(listOf(2), a.tags.map { it.nth })
        assertFalse(a.prune(3000))
        assertTrue(a.prune(2000 + ARRIVAL_TTL_MS - 100)); assertTrue(a.tags.single().leaving)
        assertTrue(a.prune(2000 + ARRIVAL_TTL_MS)); assertTrue(a.tags.isEmpty())
    }
}
