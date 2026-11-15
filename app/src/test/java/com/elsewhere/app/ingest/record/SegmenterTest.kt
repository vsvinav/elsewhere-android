package com.elsewhere.app.ingest.record

import com.elsewhere.app.ingest.haversineKm
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SegmenterTest {
    private val t0 = 1_800_000_000L
    private val home = 42.3600 to -71.0600
    private val shop = 42.3700 to -71.0600 // ~1.1 km north

    private fun fix(ts: Long, at: Pair<Double, Double>, acc: Float = 10f, speed: Float? = null, hint: String? = null, dLat: Double = 0.0, dLng: Double = 0.0) =
        Fix(ts, at.first + dLat, at.second + dLng, acc, speed, hint)

    private fun seg() = Segmenter(tzAt = { -300 })

    /** 15 min at home, a 15 min walk north, 15 min at the shop. */
    private fun dayOut(s: Segmenter, speed: Float? = null, hint: String? = null): List<Emission> {
        val out = ArrayList<Emission>()
        for (i in 0..15) out += s.feed(fix(t0 + i * 60, home))
        for (i in 1..13) out += s.feed(fix(t0 + 15 * 60 + i * 60, home, speed = speed, hint = hint, dLat = 0.0007 * i))
        for (i in 0..15) out += s.feed(fix(t0 + 30 * 60 + i * 60, shop))
        return out
    }

    @Test fun `stay, move, stay yields visit, activity, visit`() {
        val s = seg()
        val out = dayOut(s) + s.feed(fix(t0 + 60 * 60, home))
        assertEquals(listOf("visit", "activity", "visit"), out.map { it.kind })
        val v1 = (out[0] as Emission.Visit).visit
        val a = (out[1] as Emission.Activity).activity
        val path = (out[1] as Emission.Activity).path
        val v2 = (out[2] as Emission.Visit).visit

        assertEquals(t0, v1.start); assertEquals(t0 + 15 * 60, v1.end)
        assertEquals(-300, v1.tz)
        assertEquals(devicePlaceId(home.first, home.second), v1.placeId)
        assertEquals("UNKNOWN", v1.type)
        assertEquals(v1.end, a.start); assertEquals(v2.start, a.end)
        assertEquals(home.first, a.sLat, 1e-4); assertEquals(shop.first, a.eLat, 1e-4)
        assertEquals("WALKING", a.mode)
        assertTrue(abs(a.m - 1112) < 60, "m was ${a.m}")
        assertEquals(a.start, path.start); assertEquals(a.end, path.end)
        assertEquals(a.start, path.pts.first().ts); assertEquals(a.end, path.pts.last().ts)
        assertEquals(t0 + 30 * 60, v2.start)
        assertEquals(devicePlaceId(shop.first, shop.second), v2.placeId)
    }

    @Test fun `the second visit is still open until the next move`() {
        val s = seg()
        dayOut(s)
        val out = s.feed(fix(t0 + 60 * 60, home))
        assertEquals(listOf("visit"), out.map { it.kind })
        assertEquals(t0 + 45 * 60, (out[0] as Emission.Visit).visit.end)
    }

    @Test fun `a fix with poor accuracy is ignored`() {
        val s = seg()
        for (i in 0..10) s.feed(fix(t0 + i * 60, home))
        val noisy = s.feed(fix(t0 + 11 * 60, shop, acc = 250f))
        assertTrue(noisy.isEmpty())
        val out = s.feed(fix(t0 + 12 * 60, home, dLat = 0.01))
        assertEquals(t0 + 10 * 60, (out.single() as Emission.Visit).visit.end)
    }

    @Test fun `a stay is confirmed by heartbeat fixes only`() {
        // One good fix on arrival, then eight hours of coarse fixes that all contain the spot.
        val s = seg()
        s.feed(fix(t0, home))
        for (i in 1..160) s.feed(fix(t0 + i * 180, home, acc = 900f, dLat = 0.002))
        val out = s.feed(fix(t0 + 8 * 3600 + 60, shop))
        val v = (out.single() as Emission.Visit).visit
        assertEquals(t0, v.start); assertEquals(t0 + 160 * 180, v.end)
        // The centroid stayed where the good fix put it.
        assertTrue(abs(v.lat - home.first) < 1e-6)
    }

    @Test fun `a coarse fix far from the stay is still ignored`() {
        val s = seg()
        for (i in 0..10) s.feed(fix(t0 + i * 60, home))
        assertTrue(s.feed(fix(t0 + 11 * 60, home, acc = 150f, dLat = 0.01)).isEmpty())
        assertEquals(t0 + 10 * 60, s.state.stay!!.lastTs)
    }

    @Test fun `a fix that is not newer than the last one is ignored`() {
        val s = seg()
        for (i in 0..10) s.feed(fix(t0 + i * 60, home))
        assertTrue(s.feed(fix(t0 + 5 * 60, shop)).isEmpty())
        assertTrue(s.feed(fix(t0 + 10 * 60, shop)).isEmpty())
        assertEquals(t0 + 10 * 60, s.state.lastTs)
        assertEquals(11, s.state.stay!!.n)
    }

    @Test fun `GPS jitter inside the radius keeps one visit`() {
        val s = seg()
        val out = ArrayList<Emission>()
        for (i in 0..20) out += s.feed(fix(t0 + i * 60, home, dLat = if (i % 2 == 0) 0.0003 else -0.0003))
        out += s.feed(fix(t0 + 21 * 60, shop))
        val v = out.single() as Emission.Visit
        assertEquals(t0, v.visit.start); assertEquals(t0 + 20 * 60, v.visit.end)
        assertTrue(haversineKm(v.visit.lat, v.visit.lng, home.first, home.second) < 0.02)
    }

    @Test fun `a two minute pause does not split the leg`() {
        val s = seg()
        val out = ArrayList<Emission>()
        for (i in 0..10) out += s.feed(fix(t0 + i * 60, home))
        // Walk north, pause 2 min at a crossing, walk on, then settle.
        for (i in 1..5) out += s.feed(fix(t0 + 10 * 60 + i * 60, home, dLat = 0.001 * i))
        for (i in 6..7) out += s.feed(fix(t0 + 10 * 60 + i * 60, home, dLat = 0.005))
        for (i in 8..11) out += s.feed(fix(t0 + 10 * 60 + i * 60, home, dLat = 0.005 + 0.001 * (i - 7)))
        for (i in 0..10) out += s.feed(fix(t0 + 23 * 60 + i * 60, shop))
        assertEquals(listOf("visit", "activity"), out.map { it.kind })
        val a = (out[1] as Emission.Activity).activity
        assertEquals(t0 + 10 * 60, a.start); assertEquals(t0 + 23 * 60, a.end)
    }

    @Test fun `mode follows the speed when there is no hint`() {
        assertEquals("WALKING", dayOut(seg(), speed = 1.4f).mode())
        assertEquals("CYCLING", dayOut(seg(), speed = 4f).mode())
        assertEquals("IN_PASSENGER_VEHICLE", dayOut(seg(), speed = 15f).mode())
        assertEquals("FLYING", dayOut(seg(), speed = 60f).mode())
    }

    @Test fun `an activity hint wins over the speed, except for flying`() {
        assertEquals("CYCLING", dayOut(seg(), speed = 15f, hint = "ON_BICYCLE").mode())
        assertEquals("IN_PASSENGER_VEHICLE", dayOut(seg(), speed = 1f, hint = "IN_VEHICLE").mode())
        assertEquals("RUNNING", dayOut(seg(), speed = 3f, hint = "RUNNING").mode())
        assertEquals("FLYING", dayOut(seg(), speed = 60f, hint = "IN_VEHICLE").mode())
    }

    private fun List<Emission>.mode() = filterIsInstance<Emission.Activity>().single().activity.mode

    @Test fun `the path is downsampled to 25 m or 30 s`() {
        val s = seg()
        for (i in 0..10) s.feed(fix(t0 + i * 60, home))
        // Fixes every 5 s moving ~10 m each: only every 25 m (or 30 s) survives.
        var t = t0 + 10 * 60
        for (i in 1..60) { t += 5; s.feed(fix(t, home, dLat = 0.00009 * i)) }
        val out = ArrayList<Emission>()
        for (i in 0..10) out += s.feed(fix(t + 60 + i * 60, home, dLat = 0.006))
        val path = out.filterIsInstance<Emission.Activity>().single().path
        assertTrue(path.pts.size in 15..22, "kept ${path.pts.size}")
        path.pts.zipWithNext().forEach { (a, b) -> assertTrue(b.ts - a.ts >= 30 || haversineKm(a.lat, a.lng, b.lat, b.lng) >= 0.025) }
    }

    @Test fun `distance across the antimeridian is short, not a lap of the globe`() {
        val s = seg()
        val fiji = 0.0 to 179.9990
        for (i in 0..10) s.feed(fix(t0 + i * 60, fiji))
        for (i in 1..9) s.feed(fix(t0 + 10 * 60 + i * 60, fiji, dLng = 0.0001 * i))
        val out = ArrayList<Emission>()
        for (i in 0..10) out += s.feed(fix(t0 + 20 * 60 + i * 60, 0.0 to -179.9990))
        val a = out.filterIsInstance<Emission.Activity>().single().activity
        assertTrue(a.m < 500, "m was ${a.m}")
    }

    @Test fun `a long gap closes the open visit and drops the half leg`() {
        val s = seg()
        for (i in 0..10) s.feed(fix(t0 + i * 60, home))
        val out = s.feed(fix(t0 + 5 * 3600, shop))
        assertEquals(listOf("visit"), out.map { it.kind })
        assertEquals(t0 + 10 * 60, (out[0] as Emission.Visit).visit.end)
        // No leg is invented across the gap: the next stay closes on its own.
        val later = ArrayList<Emission>()
        for (i in 1..10) later += s.feed(fix(t0 + 5 * 3600 + i * 60, shop))
        later += s.feed(fix(t0 + 5 * 3600 + 11 * 60, home))
        assertEquals(listOf("visit"), later.map { it.kind })
    }

    @Test fun `close emits a confirmed stay and nothing else`() {
        val s = seg()
        for (i in 0..10) s.feed(fix(t0 + i * 60, home))
        val out = s.close()
        assertEquals(t0 + 10 * 60, (out.single() as Emission.Visit).visit.end)
        assertTrue(s.close().isEmpty())
        assertNull(s.state.stay)
    }

    @Test fun `state survives a round trip through JSON`() {
        val s = seg()
        for (i in 0..10) s.feed(fix(t0 + i * 60, home))
        s.feed(fix(t0 + 11 * 60, home, dLat = 0.002))
        val json = Json.encodeToString(SegmenterState.serializer(), s.state)
        val s2 = Segmenter(Json.decodeFromString(SegmenterState.serializer(), json), tzAt = { -300 })
        val out = ArrayList<Emission>()
        for (i in 0..10) out += s2.feed(fix(t0 + 20 * 60 + i * 60, shop))
        val a = out.filterIsInstance<Emission.Activity>().single().activity
        assertEquals(t0 + 10 * 60, a.start); assertEquals(home.first, a.sLat, 1e-6)
    }
}
