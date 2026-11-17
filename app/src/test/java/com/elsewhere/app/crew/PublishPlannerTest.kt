package com.elsewhere.app.crew

import com.elsewhere.app.ingest.Cities
import com.elsewhere.app.ingest.haversineKm
import com.elsewhere.app.ingest.record.geohash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PublishPlannerTest {
    private val cities = Cities(
        lat = doubleArrayOf(38.7223, 41.1579, 64.1466), lng = doubleArrayOf(-9.1393, -8.6291, -21.9426),
        name = listOf("Lisbon", "Porto", "Reykjavík"), cc = listOf("PT", "PT", "IS"),
    )
    private val lookup = CityLookup(cities)
    private fun friend(id: String, grants: Grants) = Friend(id, id, Crypto.hex(ByteArray(32)), Crypto.hex(ByteArray(32)), "PAIR$id", Crypto.b64(ByteArray(32)), 0L, grants)
    private val precise = friend("P", Grants(livePrecise = true))
    private val sonar = friend("S", Grants(sonar = true))
    private val city = friend("C", Grants(liveCity = true))
    private val nothing = friend("N", Grants())
    private val all = listOf(precise, sonar, city, nothing)
    private val t0 = 1_763_222_520_000L
    private val session = ShareSession(setOf("P", "S", "C", "N"), t0, Long.MAX_VALUE)
    private fun fix(ms: Long, lat: Double = 38.7250, lng: Double = -9.1400) = MyFix(ms / 1000, lat, lng, habitat = false, absenceDays = 212)

    @Test fun `grade precedence is precise over sonar over city`() {
        val p = PublishPlanner(lookup)
        assertEquals(Grade.precise, p.gradeFor(Grants(liveCity = true, livePrecise = true, sonar = true)))
        assertEquals(Grade.sonar, p.gradeFor(Grants(liveCity = true, sonar = true)))
        assertEquals(Grade.city, p.gradeFor(Grants(liveCity = true)))
        assertNull(p.gradeFor(Grants()))
    }

    @Test fun `sonar and city friends get pings with or without a share, precise only inside one`() {
        val out = PublishPlanner(lookup).plan(fix(t0), all, session, t0)
        assertEquals(listOf("P", "S", "C"), out.map { it.first.deviceId })
        assertEquals(Grade.precise, out[0].second.grade)
        for (s in listOf(null, session.copy(friendIds = setOf("N")), session.copy(until = t0))) {
            val without = PublishPlanner(lookup).plan(fix(t0), all, s, t0)
            assertEquals(listOf("S", "C"), without.map { it.first.deviceId }, "$s")
            assertEquals(listOf(Grade.sonar, Grade.city), without.map { it.second.grade }, "$s")
        }
        val both = friend("B", Grants(livePrecise = true, sonar = true))
        assertEquals(Grade.sonar, PublishPlanner(lookup).plan(fix(t0), listOf(both), null, t0).single().second.grade)
        assertEquals(Grade.precise, PublishPlanner(lookup).plan(fix(t0), listOf(both), session.copy(friendIds = setOf("B")), t0).single().second.grade)
    }

    @Test fun `a forced plan ignores the cadence`() {
        val p = PublishPlanner(lookup)
        p.plan(fix(t0), all, null, t0)
        assertEquals(emptyList<Pair<Friend, Ping>>(), p.plan(fix(t0 + 1_000), all, null, t0 + 1_000))
        assertEquals(listOf("S", "C"), p.plan(fix(t0 + 1_000), all, null, t0 + 1_000, force = true).map { it.first.deviceId })
    }

    @Test fun `precise and sonar go every 30 s, city every 5 min`() {
        val p = PublishPlanner(lookup)
        fun at(ms: Long) = p.plan(fix(t0 + ms), all, session, t0 + ms).map { it.first.deviceId }
        assertEquals(listOf("P", "S", "C"), at(0))
        assertEquals(emptyList<String>(), at(29_000))
        assertEquals(listOf("P", "S"), at(30_000))
        assertEquals(listOf("P", "S"), at(270_000))
        assertEquals(emptyList<String>(), at(299_000))
        assertEquals(listOf("P", "S", "C"), at(300_000))
    }

    @Test fun `each grade carries its own kind of position`() {
        val out = PublishPlanner(lookup).plan(fix(t0), all, session, t0).associate { it.first.deviceId to it.second }
        assertEquals(38.7250 to -9.1400, out["P"]!!.lat to out["P"]!!.lng)
        assertEquals(Grade.precise, out["P"]!!.grade)
        val s = out["S"]!!
        assertEquals(Grade.sonar, s.grade)
        assertEquals(geohash(38.7250, -9.1400, 7), geohash(s.lat!!, s.lng!!, 7), "cell centre stays in the cell")
        assertNotEquals(38.7250, s.lat)
        val c = out["C"]!!
        assertEquals(Grade.city, c.grade)
        assertEquals("Lisbon", c.city)
        val km = haversineKm(38.7223, -9.1393, c.lat!!, c.lng!!)
        assertTrue(km <= 7.1 && km > 0.0, "jitter $km km from the centroid")
        assertEquals(1_763_222_520L, c.t); assertEquals(212, c.absenceDays); assertEquals(false, c.habitat)
    }

    @Test fun `the city point holds still in town, moves with the city, and differs per pair`() {
        val a = PublishPlanner.cityPoint("PAIR1", CityPlace("Lisbon", 38.7223, -9.1393), 38.70, -9.10)
        val b = PublishPlanner.cityPoint("PAIR1", CityPlace("Lisbon", 38.7223, -9.1393), 38.75, -9.20)
        assertEquals(a, b)
        assertNotEquals(a, PublishPlanner.cityPoint("PAIR2", CityPlace("Lisbon", 38.7223, -9.1393), 38.70, -9.10))
        assertNotEquals(a, PublishPlanner.cityPoint("PAIR1", CityPlace("Porto", 41.1579, -8.6291), 41.15, -8.60))
        val wild = PublishPlanner.cityPoint("PAIR1", null, 10.123, 20.456)
        assertTrue(haversineKm(10.15, 20.45, wild.first, wild.second) < 8.0)
        assertEquals(wild, PublishPlanner.cityPoint("PAIR1", null, 10.17, 20.49))
    }

    @Test fun `a sonar hit goes out at once and only once`() {
        val p = PublishPlanner(lookup)
        p.plan(fix(t0), all, session, t0)
        p.carryHit("S", Ping.Hit("PAIRS:1", 1))
        val out = p.plan(fix(t0 + 1_000), all, session, t0 + 1_000)
        assertEquals(listOf("S"), out.map { it.first.deviceId })
        assertEquals(Ping.Hit("PAIRS:1", 1), out.single().second.hit)
        assertNull(p.plan(fix(t0 + 31_000), all, session, t0 + 31_000).first { it.first.deviceId == "S" }.second.hit)
    }

    @Test fun `city lookup finds the centroid within reach`() {
        assertEquals(CityPlace("Lisbon", 38.7223, -9.1393), lookup.at(38.75, -9.15))
        assertNull(lookup.at(0.0, 0.0))
    }
}
