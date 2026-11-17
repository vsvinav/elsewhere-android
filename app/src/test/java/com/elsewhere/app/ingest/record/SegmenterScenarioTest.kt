package com.elsewhere.app.ingest.record

import com.elsewhere.app.ingest.PActivity
import com.elsewhere.app.ingest.PVisit
import com.elsewhere.app.ingest.haversineKm
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Whole days of synthetic fixes, shaped like what a phone really delivers: one fix a minute on the move,
 * one every three minutes when still, speeds that wander, and the odd coarse fix that must be ignored.
 * Each scenario asserts the exact run of visits and legs that comes out, with modes and confidence.
 */
class SegmenterScenarioTest {
    private data class LL(val lat: Double, val lng: Double)

    private val t0 = 1_800_000_000L
    private val home = LL(42.3600, -71.0600)
    private val office = LL(42.5400, -71.0600) // 20 km north
    private val cafe = LL(42.5481, -71.0600) // 900 m north of the office
    private val station = LL(42.3690, -71.0600) // 1 km north of home
    private val farTown = LL(43.8900, -71.0600) // ~170 km north
    private val airportA = LL(42.3656, -71.0096)
    private val airportB = LL(28.8656, -71.0096) // ~1 500 km south

    private fun km(a: LL, b: LL) = haversineKm(a.lat, a.lng, b.lat, b.lng)
    private fun kmh(mps: Float) = mps * 3.6

    /** Builds one fix stream; every helper continues from the last timestamp, and `noisy` sprinkles coarse fixes in. */
    private inner class Stream(private val noisy: Boolean = true) {
        val fixes = ArrayList<Fix>()
        private val rnd = Random(7)
        private var at = home
        private fun last() = fixes.lastOrNull()?.ts ?: t0

        /** Still at `where` for `seconds`: a fix every 180 s with a few metres of jitter; the first fix is the arrival fix unless the stream is empty. */
        fun stay(where: LL, seconds: Long): Stream {
            if (fixes.isEmpty()) fixes += Fix(t0, where.lat, where.lng, 8f)
            val start = last()
            val n = (seconds / 180).toInt()
            for (k in 1..n) {
                val ts = start + 180L * k
                fixes += Fix(ts, where.lat + rnd.nextDouble(-0.00005, 0.00005), where.lng + rnd.nextDouble(-0.00005, 0.00005), 6f + rnd.nextInt(20))
                // A coarse fix whose circle still covers the stay: a heartbeat, not a move.
                if (noisy && k % 9 == 4 && k < n) fixes += Fix(ts + 90, where.lat + 0.0011, where.lng, 300f, null, "STILL")
            }
            at = where
            return this
        }

        /** A straight leg from the current spot to `to`, one fix a minute, speeds from `speed(i, n)` in m/s. */
        fun move(to: LL, seconds: Long, hint: String?, speed: (Int, Int) -> Float): Stream {
            val from = at
            val start = last()
            val n = (seconds / 60).toInt()
            for (i in 1..n) {
                val f = i.toDouble() / n
                val ts = start + 60L * i
                fixes += Fix(ts, from.lat + (to.lat - from.lat) * f, from.lng + (to.lng - from.lng) * f, 8f + rnd.nextInt(12), speed(i, n), hint)
                // A coarse fix two kilometres off the road: too vague to place, so it must vanish.
                if (noisy && i % 7 == 3 && i < n) fixes += Fix(ts + 30, from.lat + (to.lat - from.lat) * f + 0.02, from.lng, 400f, null, hint)
            }
            at = to
            return this
        }

        /** A ride round a circle of `radiusM` that starts and ends at the current spot. */
        fun loop(radiusM: Double, seconds: Long, hint: String?, speed: (Int, Int) -> Float): Stream {
            val from = at
            val start = last()
            val n = (seconds / 60).toInt()
            val dLat = radiusM / 111_320.0
            val dLng = dLat / cos(Math.toRadians(from.lat))
            val c = LL(from.lat + dLat, from.lng)
            for (i in 1..n) {
                val th = 2 * PI * i / n
                val ts = start + 60L * i
                val p = if (i == n) from else LL(c.lat - dLat * cos(th), c.lng + dLng * sin(th))
                fixes += Fix(ts, p.lat, p.lng, 9f, speed(i, n), hint)
            }
            return this
        }

        /** The phone goes dark for `seconds`; `land` picks up after. */
        fun gap(seconds: Long): Stream { dark = seconds; return this }
        private var dark = 0L

        /** Lands at `where` after a gap: a single good fix, then still for `seconds`. */
        fun land(where: LL, seconds: Long): Stream {
            fixes += Fix(last() + dark, where.lat, where.lng, 10f)
            dark = 0
            return stay(where, seconds)
        }
    }

    private fun carSpeed(rnd: Random) = { _: Int, _: Int -> rnd.nextDouble(40.0, 70.0).div(3.6).toFloat() }
    private fun walkSpeed(rnd: Random) = { _: Int, _: Int -> rnd.nextDouble(1.3, 1.6).toFloat() }
    /** Ten rungs from 90 to 170 km/h, so the fast tenth of the readings is well over 140. */
    private val trainSpeed = { i: Int, _: Int -> ((90.0 + 80.0 * (i % 10) / 9) / 3.6).toFloat() }
    private val bikeSpeed = { _: Int, _: Int -> (15.0 / 3.6).toFloat() }

    private fun run(fixes: List<Fix>, tzAt: (Long) -> Int = { -300 }, close: Boolean = true): Pair<List<Emission>, Segmenter> {
        val s = Segmenter(tzAt = tzAt)
        val out = ArrayList<Emission>()
        for (f in fixes) out += s.feed(f)
        if (close) out += s.close()
        return out to s
    }

    private fun List<Emission>.kinds() = map { it.kind }
    private fun List<Emission>.visit(i: Int): PVisit = (this[i] as Emission.Visit).visit
    private fun List<Emission>.leg(i: Int): PActivity = (this[i] as Emission.Activity).activity

    private fun assertNear(expected: LL, lat: Double, lng: Double, metres: Double = 20.0) =
        assertTrue(haversineKm(expected.lat, expected.lng, lat, lng) * 1000 < metres, "expected $expected got $lat,$lng")
    private fun assertKm(expected: Double, metres: Double, pct: Double = 2.0) =
        assertTrue(abs(metres / 1000 - expected) / expected * 100 < pct, "expected ~$expected km got ${metres / 1000}")

    private fun commuteDay(): List<Fix> {
        val rnd = Random(11)
        return Stream()
            .stay(home, 7 * 3600)
            .move(office, 25 * 60, "IN_VEHICLE", carSpeed(rnd))
            .stay(office, 8 * 3600)
            .move(cafe, 10 * 60, "WALKING", walkSpeed(rnd))
            .stay(cafe, 42 * 60)
            .move(home, 25 * 60, "IN_VEHICLE", carSpeed(rnd))
            .stay(home, 30 * 60)
            .fixes
    }

    @Test fun `a commute day, home, car to the office, walk to a cafe, car home`() {
        val (out, _) = run(commuteDay())
        assertEquals(listOf("visit", "activity", "visit", "activity", "visit", "activity", "visit"), out.kinds())
        val e1 = t0 + 7 * 3600
        val s2 = e1 + 25 * 60; val e2 = s2 + 8 * 3600
        val s3 = e2 + 10 * 60; val e3 = s3 + 42 * 60
        val s4 = e3 + 25 * 60; val e4 = s4 + 30 * 60

        out.visit(0).let { assertEquals(t0, it.start); assertEquals(e1, it.end); assertNear(home, it.lat, it.lng); assertEquals(1.0, it.p); assertEquals(-300, it.tz) }
        out.leg(1).let {
            assertEquals(e1, it.start); assertEquals(s2, it.end)
            assertEquals("IN_PASSENGER_VEHICLE", it.mode); assertEquals(0.6, it.p)
            assertNear(home, it.sLat, it.sLng); assertNear(office, it.eLat, it.eLng)
            assertKm(km(home, office), it.m)
        }
        out.visit(2).let { assertEquals(s2, it.start); assertEquals(e2, it.end); assertNear(office, it.lat, it.lng) }
        out.leg(3).let {
            assertEquals(e2, it.start); assertEquals(s3, it.end)
            assertEquals("WALKING", it.mode); assertEquals(1.0, it.p)
            assertKm(km(office, cafe), it.m)
        }
        out.visit(4).let { assertEquals(s3, it.start); assertEquals(e3, it.end); assertNear(cafe, it.lat, it.lng) }
        out.leg(5).let { assertEquals(e3, it.start); assertEquals(s4, it.end); assertEquals("IN_PASSENGER_VEHICLE", it.mode); assertEquals(0.6, it.p); assertNear(home, it.eLat, it.eLng) }
        out.visit(6).let { assertEquals(s4, it.start); assertEquals(e4, it.end); assertNear(home, it.lat, it.lng) }
        // Every leg's id is its start, so a replay lands on the same row.
        for (e in out) if (e is Emission.Activity) assertEquals(e.activity.start.toInt(), e.activity.id)
        for (e in out) if (e is Emission.Activity) { assertEquals(e.activity.start, e.path.start); assertEquals(e.activity.end, e.path.end); assertEquals(e.activity.end, e.path.pts.last().ts) }
    }

    @Test fun `the coarse fixes changed nothing`() {
        val rnd = Random(11)
        val clean = Stream(noisy = false)
            .stay(home, 7 * 3600).move(office, 25 * 60, "IN_VEHICLE", carSpeed(rnd)).stay(office, 8 * 3600)
            .move(cafe, 10 * 60, "WALKING", walkSpeed(rnd)).stay(cafe, 42 * 60).move(home, 25 * 60, "IN_VEHICLE", carSpeed(rnd)).stay(home, 30 * 60).fixes
        val noisy = commuteDay()
        assertTrue(noisy.size > clean.size)
        val a = run(clean).first; val b = run(noisy).first
        assertEquals(a.size, b.size)
        a.zip(b).forEach { (x, y) ->
            when (x) {
                is Emission.Visit -> { val v = (y as Emission.Visit).visit; assertEquals(x.visit.start, v.start); assertEquals(x.visit.end, v.end) }
                is Emission.Activity -> { val l = (y as Emission.Activity).activity; assertEquals(x.activity.start, l.start); assertEquals(x.activity.end, l.end); assertEquals(x.activity.mode, l.mode); assertEquals(x.activity.p, l.p); assertEquals(x.path.pts.size, y.path.pts.size) }
            }
        }
    }

    @Test fun `a train trip, walk to the station, then eighty minutes with IN_VEHICLE hints at train speeds`() {
        val rnd = Random(3)
        val fixes = Stream()
            .stay(home, 3600)
            .move(station, 12 * 60, "WALKING", walkSpeed(rnd))
            .stay(station, 12 * 60)
            .move(farTown, 80 * 60, "IN_VEHICLE", trainSpeed)
            .stay(farTown, 30 * 60)
            .fixes
        val (out, _) = run(fixes)
        assertEquals(listOf("visit", "activity", "visit", "activity", "visit"), out.kinds())
        val e1 = t0 + 3600; val s2 = e1 + 12 * 60; val e2 = s2 + 12 * 60; val s3 = e2 + 80 * 60
        out.leg(1).let { assertEquals("WALKING", it.mode); assertEquals(1.0, it.p); assertEquals(e1, it.start); assertEquals(s2, it.end) }
        out.visit(2).let { assertEquals(s2, it.start); assertEquals(e2, it.end); assertNear(station, it.lat, it.lng) }
        out.leg(3).let {
            assertEquals("IN_TRAIN", it.mode); assertEquals(0.7, it.p)
            assertEquals(e2, it.start); assertEquals(s3, it.end)
            assertKm(km(station, farTown), it.m)
        }
        out.visit(4).let { assertEquals(s3, it.start); assertNear(farTown, it.lat, it.lng) }
    }

    @Test fun `the same ride with no hints is a car, since the speeds alone cannot say`() {
        val fixes = Stream().stay(home, 3600).move(office, 25 * 60, null, carSpeed(Random(5))).stay(office, 3600).fixes
        val (out, _) = run(fixes)
        assertEquals(listOf("visit", "activity", "visit"), out.kinds())
        assertEquals("IN_PASSENGER_VEHICLE" to 0.6, out.leg(1).let { it.mode to it.p })
    }

    @Test fun `a flight the phone slept through, stay, five dark hours, a stay 1 500 km away`() {
        val fixes = Stream().stay(airportA, 3600).gap(5 * 3600).land(airportB, 3600).fixes
        val (out, _) = run(fixes)
        assertEquals(listOf("visit", "activity", "visit"), out.kinds())
        val eA = t0 + 3600; val sB = eA + 5 * 3600
        val a = out.visit(0); val f = out.leg(1); val b = out.visit(2)
        assertEquals(t0, a.start); assertEquals(eA, a.end); assertNear(airportA, a.lat, a.lng)
        assertEquals("FLYING", f.mode); assertEquals(0.8, f.p)
        assertEquals(eA, f.start); assertEquals(sB, f.end)
        assertNear(airportA, f.sLat, f.sLng); assertNear(airportB, f.eLat, f.eLng)
        assertEquals(Math.rint(haversineKm(a.lat, a.lng, airportB.lat, airportB.lng) * 1000), f.m)
        assertKm(1500.0, f.m, pct = 1.0)
        assertEquals(listOf(eA, sB), (out[1] as Emission.Activity).path.pts.map { it.ts })
        assertEquals(sB, b.start); assertEquals(sB + 3600, b.end); assertNear(airportB, b.lat, b.lng)
    }

    @Test fun `the flight survives the stale sweep closing the airport stay before landing`() {
        val eA = t0 + 3600; val sB = eA + 5 * 3600
        val before = Stream().stay(airportA, 3600).fixes
        val after = Stream().stay(airportA, 3600).gap(5 * 3600).land(airportB, 3600).fixes.drop(before.size)
        val s = Segmenter(tzAt = { -300 })
        val out = ArrayList<Emission>()
        for (f in before) out += s.feed(f)
        // An hour of silence and the sweep closes the stay, as RecordWorker does; the leg must still appear on landing.
        out += s.close()
        assertEquals(listOf("visit"), out.kinds())
        val restored = Segmenter(Json.decodeFromString(SegmenterState.serializer(), Json.encodeToString(SegmenterState.serializer(), s.state)), tzAt = { -300 })
        for (f in after) out += restored.feed(f)
        out += restored.close()
        assertEquals(listOf("visit", "activity", "visit"), out.kinds())
        val a = out.visit(0); val f = out.leg(1); val b = out.visit(2)
        assertEquals(eA, a.end)
        assertEquals("FLYING", f.mode); assertEquals(0.8, f.p)
        assertEquals(a.end, f.start); assertEquals(sB, f.end)
        assertEquals(a.lat, f.sLat); assertEquals(a.lng, f.sLng)
        assertNear(airportB, f.eLat, f.eLng)
        assertEquals(sB, b.start); assertNear(airportB, b.lat, b.lng)
    }

    @Test fun `state saved before the flight-leg field existed still restores`() {
        val old = Json.decodeFromString(SegmenterState.serializer(), """{"lastTs":${t0}}""")
        assertEquals(t0, old.lastTs); assertEquals(null, old.last)
    }

    @Test fun `a flight across time zones, each visit keeps the zone it was in, the flight the one it left`() {
        val eA = t0 + 3600; val sB = eA + 5 * 3600
        val fixes = Stream().stay(airportA, 3600).gap(5 * 3600).land(airportB, 3600).fixes
        val (out, _) = run(fixes, tzAt = { ts -> if (ts >= sB) -240 else -300 })
        assertEquals(listOf("visit", "activity", "visit"), out.kinds())
        assertEquals(-300, out.visit(0).tz)
        assertEquals(-300, out.leg(1).tz)
        assertEquals(-240, out.visit(2).tz)
    }

    @Test fun `a dead phone at home for three hours is still one stay, with no leg invented`() {
        val rnd = Random(2)
        val fixes = Stream().stay(home, 3 * 3600).gap(3 * 3600).land(home, 3600).move(station, 12 * 60, "WALKING", walkSpeed(rnd)).stay(station, 21 * 60).fixes
        val (out, _) = run(fixes)
        assertEquals(listOf("visit", "activity", "visit"), out.kinds())
        val e1 = t0 + 7 * 3600
        out.visit(0).let { assertEquals(t0, it.start); assertEquals(e1, it.end); assertNear(home, it.lat, it.lng) }
        out.leg(1).let { assertEquals("WALKING", it.mode); assertEquals(e1, it.start); assertEquals(e1 + 12 * 60, it.end) }
    }

    @Test fun `a short dark spell that lands nearby splits the stays and invents nothing`() {
        val fixes = Stream().stay(home, 3600).gap(3 * 3600).land(station, 3600).fixes
        val (out, _) = run(fixes)
        assertEquals(listOf("visit", "visit"), out.kinds())
        assertEquals(t0 + 3600, out.visit(0).end)
        assertEquals(t0 + 3600 + 3 * 3600, out.visit(1).start)
    }

    @Test fun `a cycling loop, sure with ON_BICYCLE hints, a guess at 15 km per hour without`() {
        for ((hint, p) in listOf("ON_BICYCLE" to 1.0, null to 0.8)) {
            val fixes = Stream().stay(home, 30 * 60).loop(800.0, 20 * 60, hint, bikeSpeed).stay(home, 30 * 60).fixes
            val (out, _) = run(fixes)
            assertEquals(listOf("visit", "activity", "visit"), out.kinds(), "hint $hint")
            val e1 = t0 + 30 * 60; val s2 = e1 + 20 * 60
            out.leg(1).let {
                assertEquals("CYCLING", it.mode, "hint $hint"); assertEquals(p, it.p, "hint $hint")
                assertEquals(e1, it.start); assertEquals(s2, it.end)
                assertNear(home, it.sLat, it.sLng); assertNear(home, it.eLat, it.eLng)
                assertKm(2 * PI * 0.8, it.m, pct = 5.0)
            }
            out.visit(2).let { assertEquals(s2, it.start); assertEquals(s2 + 30 * 60, it.end) }
        }
    }

    @Test fun `replaying the day into a restored segmenter emits nothing twice`() {
        val fixes = commuteDay()
        val (first, s) = run(fixes, close = false)
        assertEquals(6, first.size)
        // The same recorder fed the same fixes again.
        for (f in fixes) assertTrue(s.feed(f).isEmpty(), "replayed ${f.ts}")
        // A restarted recorder, rebuilt from the persisted state.
        val json = Json.encodeToString(SegmenterState.serializer(), s.state)
        val s2 = Segmenter(Json.decodeFromString(SegmenterState.serializer(), json), tzAt = { -300 })
        for (f in fixes) assertTrue(s2.feed(f).isEmpty(), "replayed ${f.ts} after restart")
        // Both still hold the open stay at home and close it the same way.
        assertEquals(s.close(), s2.close())
    }

    @Test fun `a restart halfway through a leg loses nothing`() {
        val rnd = Random(11)
        val fixes = Stream().stay(home, 3600).move(office, 25 * 60, "IN_VEHICLE", carSpeed(rnd)).stay(office, 3600).fixes
        val whole = run(fixes).first
        val cut = fixes.indexOfFirst { it.ts > t0 + 3600 + 12 * 60 }
        val s1 = Segmenter(tzAt = { -300 })
        val out = ArrayList<Emission>()
        for (f in fixes.subList(0, cut)) out += s1.feed(f)
        val s2 = Segmenter(Json.decodeFromString(SegmenterState.serializer(), Json.encodeToString(SegmenterState.serializer(), s1.state)), tzAt = { -300 })
        for (f in fixes.subList(cut, fixes.size)) out += s2.feed(f)
        out += s2.close()
        assertEquals(whole.map { it.kind }, out.kinds())
        assertEquals(whole.map { (it as? Emission.Activity)?.activity ?: (it as Emission.Visit).visit }, out.map { (it as? Emission.Activity)?.activity ?: (it as Emission.Visit).visit })
    }

    @Test fun `speeds in the generated legs are what the scenarios claim`() {
        val car = Stream().stay(home, 600).move(office, 25 * 60, "IN_VEHICLE", carSpeed(Random(11))).fixes.mapNotNull { it.speed }
        assertTrue(car.all { kmh(it) in 40.0..70.0 })
        val train = Stream().stay(home, 600).move(farTown, 80 * 60, "IN_VEHICLE", trainSpeed).fixes.mapNotNull { it.speed }.sorted()
        assertTrue(kmh(train[train.size * 9 / 10]) > 140 && kmh(train[train.size / 2]) < 180)
    }
}
