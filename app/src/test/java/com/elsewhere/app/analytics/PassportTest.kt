package com.elsewhere.app.analytics

import com.elsewhere.app.Fx
import com.elsewhere.app.Fx.D
import com.elsewhere.app.Fx.H
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.ui.nav.Door
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

// Mirrors web/src/analytics/passport.test.ts, case for case.
class PassportTest {
    private val jan = Fx.utc(2025, 0, 1)
    private fun v(start: Long, end: Long, id: String, lat: Double, lng: Double, tz: Int = 0) = Fx.visit(start, end, id, lat = lat, lng = lng, tz = tz)
    private fun act(start: Long, end: Long, m: Double) = Fx.act(start, end, "FLYING", m)

    /**
     * Three countries, five cities and one stop nowhere: Paris 3 nights, Lyon 2, Tokyo 5, Rome 2, Osaka 2, Atlantis 1 — 15 nights,
     * so 10 is earned and 100 is not. One 45,000 km flight laps the Earth; every visit sits in one time zone.
     */
    private val data = Fx.record(
        visits = listOf(
            v(jan, jan + 3 * D, "home", 42.36, -71.06),
            v(jan + 4 * D, jan + 7 * D - 12 * H, "paris", 48.85, 2.35),
            v(jan + 9 * D, jan + 11 * D - 12 * H, "lyon", 45.76, 4.84),
            v(jan + 14 * D, jan + 19 * D - 12 * H, "tokyo", 35.68, 139.69),
            v(jan + 24 * D, jan + 26 * D - 12 * H, "rome", 41.9, 12.5),
            v(jan + 31 * D, jan + 33 * D - 12 * H, "osaka", 34.69, 135.5),
            v(jan + 40 * D, jan + 41 * D - 12 * H, "atlantis", 30.0, -40.0),
        ),
        activities = listOf(act(jan + 2 * H, jan + 3 * H, 40_000.0), act(jan + 4 * D - 10 * H, jan + 4 * D - 2 * H, 45_000_000.0)),
        places = mapOf(
            "home" to Fx.place(com.elsewhere.app.data.Role.HOME, "Boston", "US", lat = 42.36, lng = -71.06),
            "paris" to Fx.place(null, "Paris", "FR", lat = 48.85, lng = 2.35),
            "lyon" to Fx.place(null, "Lyon", "FR", lat = 45.76, lng = 4.84),
            "tokyo" to Fx.place(null, "Tokyo", "JP", lat = 35.68, lng = 139.69),
            "rome" to Fx.place(null, "Rome", "IT", lat = 41.9, lng = 12.5),
            "osaka" to Fx.place(null, "Osaka", "JP", lat = 34.69, lng = 135.5),
            "atlantis" to Fx.place(null, "Atlantis", null, lat = 30.0, lng = -40.0),
        ),
        home = LatLng(42.36, -71.06), range = jan..jan + 45 * D,
    )
    private val p = passport(data)
    private fun page(id: String) = p.pages.first { it.id == id }
    private fun stamp(pageId: String, id: String) = page(pageId).stamps.first { it.id == id }

    @Test fun `one page per country in first-earned order, stops nowhere on ELSEWHERE, milestones last`() {
        assertEquals(listOf("FR", "JP", "IT", "ELSEWHERE", "MILESTONES"), p.pages.map { it.title })
        assertEquals(listOf(PageKind.COUNTRY, PageKind.COUNTRY, PageKind.COUNTRY, PageKind.COUNTRY, PageKind.MILESTONE), p.pages.map { it.kind })
    }

    @Test fun `city stamps are dated on the first night, carry the nights and open the beds`() {
        assertEquals(listOf("PARIS", "LYON"), page("country:FR").stamps.map { it.label })
        val paris = stamp("country:FR", "city:Paris")
        assertEquals(jan + 4 * D, paris.earnedAt)
        assertEquals("3 nights", paris.sub)
        assertEquals(Door.Places(listOf("paris"), "Paris"), paris.door)
        assertEquals("1 night", stamp("country:ELSEWHERE", "city:Atlantis").sub)
        assertEquals(listOf("TOKYO", "OSAKA"), page("country:JP").stamps.map { it.label })
    }

    @Test fun `milestones - earned ones dated, the rest ghosted with no door`() {
        val ms = page("milestones")
        assertEquals(
            listOf(
                "FIRST NIGHT AWAY", "10 NIGHTS AWAY", "100 NIGHTS AWAY", "1,000 NIGHTS AWAY", "5 CITIES", "25 CITIES", "100 CITIES",
                "3 COUNTRIES", "10 COUNTRIES", "25 COUNTRIES", "1 LAP OF EARTH", "10 TIME ZONES",
            ),
            ms.stamps.map { it.label },
        )
        assertEquals(jan + 4 * D, stamp("milestones", "ms:first-night").earnedAt)
        // the tenth night is the fifth in Tokyo: 5 nights before it, Tokyo's first night plus four days
        assertEquals(jan + 18 * D, stamp("milestones", "ms:nights-10").earnedAt)
        assertNull(stamp("milestones", "ms:nights-100").earnedAt)
        assertNull(stamp("milestones", "ms:nights-100").door)
        assertEquals(jan + 31 * D, stamp("milestones", "ms:cities-5").earnedAt)
        assertNull(stamp("milestones", "ms:cities-25").earnedAt)
        assertEquals(jan + 24 * D, stamp("milestones", "ms:countries-3").earnedAt)
        assertNull(stamp("milestones", "ms:countries-10").earnedAt)
        assertEquals(jan + 4 * D - 2 * H, stamp("milestones", "ms:lap").earnedAt)
        assertNull(stamp("milestones", "ms:zones").earnedAt)
        assertEquals(Door.Range(jan + 3 * D - 2 * H, jan + 5 * D - 2 * H, "1 LAP OF EARTH"), stamp("milestones", "ms:lap").door)
    }

    @Test fun `earnedIds lists every dated stamp across the booklet`() {
        assertEquals(
            listOf("city:Paris", "city:Lyon", "city:Tokyo", "city:Osaka", "city:Rome", "city:Atlantis", "ms:first-night", "ms:nights-10", "ms:cities-5", "ms:countries-3", "ms:lap"),
            p.earnedIds,
        )
    }

    @Test fun `night milestones walk the visits, so a repeat destination never dates a milestone to an earlier trip`() {
        // Paris 2015 for 2 nights, London 2016 for 10, Paris again 2024 for 50: the tenth night is London's eighth, not a 2015 date.
        val y15 = Fx.utc(2015, 5, 1); val y16 = Fx.utc(2016, 5, 1); val y24 = Fx.utc(2024, 5, 1)
        val d = data.copy(
            activities = emptyList(),
            visits = listOf(
                v(y15, y15 + 2 * D - 12 * H, "paris", 48.85, 2.35),
                v(y15 + 2 * D, y16 - D, "home", 42.36, -71.06),
                v(y16, y16 + 10 * D - 12 * H, "london", 51.5, -0.12),
                v(y16 + 10 * D, y24 - D, "home", 42.36, -71.06),
                v(y24, y24 + 50 * D - 12 * H, "paris", 48.85, 2.35),
            ),
            places = data.places + ("london" to Fx.place(null, "London", "GB", lat = 51.5, lng = -0.12)),
        )
        val r = passport(d)
        fun ms(id: String) = r.pages.first { it.id == "milestones" }.stamps.first { it.id == id }
        assertEquals("52 nights", r.pages.first { it.id == "country:FR" }.stamps[0].sub)
        assertEquals(y15, ms("ms:first-night").earnedAt)
        assertEquals(y16 + 7 * D, ms("ms:nights-10").earnedAt)
        assertNull(ms("ms:nights-100").earnedAt)
    }

    @Test fun `time zones ignore daylight saving, the same city in January and July is one zone`() {
        val jul = Fx.utc(2025, 6, 1)
        fun ny(start: Long, tz: Int) = v(start, start + 2 * D, "ny", 40.71, -74.01, tz)
        val base = data.copy(activities = emptyList(), places = data.places + ("ny" to Fx.place(null, "New York", "US", lat = 40.71, lng = -74.01)))
        fun ms(r: Passport, id: String) = r.pages.first { it.id == "milestones" }.stamps.first { it.id == id }
        assertNull(ms(passport(base.copy(visits = listOf(ny(jan, -300), ny(jul, -240)))), "ms:zones").earnedAt)
        // nine more cities, each a further 15° east: the tenth distinct zone is reached on the last of them
        val visits = mutableListOf(ny(jan, -300), ny(jul, -240))
        val places = base.places.toMutableMap()
        for (i in 1 until 10) {
            val lng = -74.01 + 15 * i
            places["z$i"] = Fx.place(null, "Zone $i", "XX", lat = 10.0, lng = lng)
            visits += v(jan + 10 * i * D, jan + 10 * i * D + 2 * D, "z$i", 10.0, lng, 0)
        }
        assertEquals(jan + 90 * D, ms(passport(base.copy(visits = visits, places = places)), "ms:zones").earnedAt)
    }

    @Test fun `an empty record is a booklet of outlines`() {
        val e = passport(data.copy(visits = emptyList(), activities = emptyList()))
        assertEquals(1, e.pages.size)
        assertTrue(e.pages[0].stamps.none { it.earned })
    }

    @Test fun `tilt is deterministic and within 6 degrees`() {
        assertEquals(tilt("city:Paris"), tilt("city:Paris"))
        assertNotEquals(tilt("city:Paris"), tilt("city:Lyon"))
        for (id in listOf("a", "ms:lap", "city:Tokyo", "country:FR")) assertTrue(abs(tilt(id)) <= 6f, id)
        // pinned to the web's FNV-1a so a stamp leans the same way on both platforms
        assertEquals(tiltWeb("city:Paris"), tilt("city:Paris"), 1e-5f)
    }

    /** The web's `tilt` written out with JS semantics (Math.imul, >>> 0), as a cross-check. */
    private fun tiltWeb(id: String): Float {
        var h = 2166136261L
        for (c in id) { h = (h xor c.code.toLong()) and 0xffffffffL; h = (h * 16777619L) and 0xffffffffL }
        return ((h % 1201) / 100.0 - 6).toFloat()
    }

    @Test fun `300k visits in a few hundred ms`() {
        val places = HashMap<String, com.elsewhere.app.data.Place>()
        places["home"] = Fx.place(com.elsewhere.app.data.Role.HOME, "Boston", "US", lat = 42.36, lng = -71.06)
        val visits = ArrayList<com.elsewhere.app.data.Visit>(300_000)
        for (i in 0 until 300_000) {
            val pid = "p${i % 2000}"
            places[pid] = Fx.place(null, "City ${i % 2000}", "C${i % 60}", lat = 10.0 + i % 50, lng = (i % 300) - 150.0)
            visits += v(jan + i * 4 * H, jan + i * 4 * H + 3 * H, pid, 10.0 + i % 50, (i % 300) - 150.0, (i % 24) * 60)
        }
        val big = data.copy(visits = visits, places = places, activities = List(100_000) { i -> act(jan + i * H, jan + i * H + 1800, 1000.0) })
        val t0 = System.nanoTime()
        val r = passport(big)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(ms < 3000, "took $ms ms")
        assertTrue(r.pages.size > 1)
    }
}
