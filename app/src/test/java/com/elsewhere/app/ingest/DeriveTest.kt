package com.elsewhere.app.ingest

import com.elsewhere.app.data.Home
import com.elsewhere.app.data.ImportReport
import com.elsewhere.app.data.Meta
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Role
import com.elsewhere.app.data.Units
import com.elsewhere.app.data.Work
import com.elsewhere.app.data.homeAt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class DeriveTest {
    private fun mk(id: Int, start: Long, placeId: String, lat: Double, lng: Double) =
        PVisit(id, start, start + 3600, 0, placeId, lat, lng, "UNKNOWN", 0.9, 0)

    @Test fun `points every label of a home at its busiest id, per dated home`() {
        val profile = Profile(null, Units.KM, homes = listOf(
            Home(42.3632761, -71.0643721, "", "", 0, 1000), Home(37.7749, -122.4194, "", "", 1000, null),
        ), works = emptyList())
        val visits = listOf(
            mk(1, 10, "a", 42.3632761, -71.0643721), mk(2, 20, "b", 42.3633, -71.0644), mk(3, 30, "b", 42.3633, -71.0644),
            mk(4, 40, "far", 42.4, -71.1), mk(5, 2000, "c", 37.7749, -122.4194), mk(6, 2100, "a", 42.3632761, -71.0643721),
        )
        assertEquals(listOf("b", "b", "b", "far", "c", "a"), foldRoles(visits, profile).map { it.placeId })
        assertEquals("a", visits[0].placeId)
    }

    @Test fun `a home you came back to stays one Home, work only counts while the job ran`() {
        val bos = Home(42.3632761, -71.0643721, "", "", 0, 1000)
        val profile = Profile(null, Units.KM,
            homes = listOf(bos, Home(37.7749, -122.4194, "", "", 1000, 2000), bos.copy(from = 2000, to = null)),
            works = listOf(Work(42.35, -71.05, "w", from = 500, to = 1500)))
        val visits = listOf(
            mk(1, 10, "a", bos.lat, bos.lng), mk(2, 20, "b", 42.3633, -71.0644), mk(3, 30, "b", 42.3633, -71.0644),
            mk(4, 1500, "c", 37.7749, -122.4194), mk(5, 2500, "a", bos.lat, bos.lng), mk(6, 2600, "a", bos.lat, bos.lng),
        )
        assertEquals(listOf("a", "a", "a", "c", "a", "a"), foldRoles(visits, profile).map { it.placeId })
        assertEquals(Role.HOME, roleOf(mk(7, 1500, "c", 37.7749, -122.4194), profile))
        assertNull(roleOf(mk(8, 1500, "a", bos.lat, bos.lng), profile))
        assertEquals(Role.WORK, roleOf(mk(9, 700, "w", 42.35, -71.05), profile))
        assertNull(roleOf(mk(10, 1700, "w", 42.35, -71.05), profile))
    }

    @Test fun `homeAt honours an explicit end date, the gap before the next home counts toward it`() {
        fun h(lat: Double, from: Long, to: Long?) = Home(lat, 0.0, "", "", from, to)
        val homes = listOf(h(1.0, 0, 100), h(2.0, 200, null))
        assertEquals(1.0, homeAt(homes, 50).lat)
        assertEquals(2.0, homeAt(homes, 150).lat)
        assertEquals(2.0, homeAt(homes, 250).lat)
        assertEquals(1.0, homeAt(listOf(h(1.0, 0, 100)), 500).lat)
    }

    @Test fun `end to end, the mini export with a one-home profile`() {
        val res = javaClass.classLoader
        val parsed = parseTimeline(res.getResource("mini_timeline.json")!!.readText())
        val cities = Cities.fromJson(res.getResource("cities.json")!!.readText())
        val profile = Profile(null, Units.KM, homes = listOf(Home(42.3632761, -71.0643721, "Boston", "US", 0, null)),
            works = listOf(Work(42.3736, -71.1097, "WORK1", from = 0, to = null)))
        val meta = Meta("mini_timeline.json", "x", 0, 0L..0L, ImportReport(0, 0, emptyMap()))
        val record = derive(parsed, profile, cities, meta)

        assertTrue(record.places.isNotEmpty() && record.daily.isNotEmpty())
        assertEquals(parsed.visits.minOf { it.start }, record.meta.range.first)
        assertEquals(maxOf(parsed.visits.maxOf { it.end }, parsed.activities.maxOf { it.end }), record.meta.range.last)
        assertEquals(parsed.report, record.meta.importReport)

        val home = record.places.getValue("HOME1")
        assertEquals(4, home.nVisits)
        assertTrue(abs(home.totalHours - (8 + 14.333 + 11 + 7.483)) < 0.1)
        assertEquals("HOME", home.label)
        assertEquals(Role.HOME, home.role)
        assertEquals("US", home.countryCode)
        assertEquals(Role.WORK, record.places.getValue("WORK1").role)
        assertNull(record.places.getValue("MTV1").role)
        assertEquals(2, record.places.getValue("MTV1").nVisits)
        assertTrue("BAD1" !in record.places)

        val byDate = record.daily.associateBy { it.date }
        assertEquals(listOf("2025-03-03", "2025-03-04", "2025-03-05", "2025-03-06", "2025-03-07", "2025-03-08", "2025-03-09"), byDate.keys.sorted())
        val d3 = byDate.getValue("2025-03-03")
        assertEquals(mapOf("IN_SUBWAY" to 10.4), d3.kmByMode)
        assertEquals(4, d3.nVisits)
        // LUNCH1 shares WORK1's coordinates, so foldRoles points it at the work id.
        assertEquals(2, d3.nPlaces)
        assertEquals(2, d3.nNewPlaces)
        assertTrue("LUNCH1" !in record.places)
        assertTrue(abs(d3.hoursHome - (8 + 6.333)) < 0.05)
        assertTrue(abs(d3.hoursWork - 8.5) < 0.01)
        assertEquals(0.0, d3.hoursOther)
        assertTrue(abs(d3.hoursTransit - (0.5 + 0.667)) < 0.01)
        assertTrue(abs(byDate.getValue("2025-03-04").hoursOther - 1.5) < 0.01)
        assertEquals(4340.0, byDate.getValue("2025-03-05").kmByMode["FLYING"])
        assertTrue(byDate.getValue("2025-03-05").maxDistFromHomeKm > 4000)

        assertEquals(2, record.trips.size)
        val t = record.trips[0]
        assertEquals(parsed.activities.first { it.mode == "FLYING" }.start, t.start)
        assertEquals(parsed.visits.last { it.placeId == "HOME1" }.start, t.end)
        assertEquals(2, t.nights)
        assertEquals(8680.0, t.totalKmByMode["FLYING"])
        assertEquals(4.0, t.totalKmByMode["CYCLING"])
        assertEquals(4340.0, t.memory!!.distanceFromOriginKm)
        assertEquals(listOf("MTV1"), t.memory!!.destinationPlaceIds)
        assertEquals(1, record.trips[1].nights)
        assertNull(record.trips[1].memory)

        assertEquals(1, record.strokes.size)
        assertEquals(3, record.strokes[0].pts.size)
        assertEquals(LatLngOf(42.3632761, -71.0643721), LatLngOf(record.profile.home.lat, record.profile.home.lng))
        assertEquals(parsed.visits.size, record.visits.size)
    }

    private data class LatLngOf(val lat: Double, val lng: Double)

    /** The ground trail must be the web's: every timelinePath, thinned by one global step, its last point always kept, 1e-4 rounding. */
    @Test fun `strokes are thinned exactly as the web thins them`() {
        val res = javaClass.classLoader
        val parsed = parseTimeline(res.getResource("mini_timeline.json")!!.readText())
        val cities = Cities.fromJson(res.getResource("cities.json")!!.readText())
        val profile = Profile(null, Units.KM, homes = listOf(Home(42.3632761, -71.0643721, "Boston", "US", 0, null)), works = emptyList())
        val meta = Meta("mini_timeline.json", "x", 0, 0L..0L, ImportReport(0, 0, emptyMap()))
        val long = PPath(0, 9002, (0 until 9002).map { i -> com.elsewhere.app.data.Pt(42.0 + i * 0.00001234, -71.0, i.toLong()) })
        val pair = PPath(0, 1, listOf(com.elsewhere.app.data.Pt(1.0, 2.0, 0), com.elsewhere.app.data.Pt(3.0, 4.0, 1)))
        val lone = PPath(0, 1, listOf(com.elsewhere.app.data.Pt(5.0, 6.0, 0)))
        val record = derive(parsed.copy(paths = listOf(long, pair, lone)), profile, cities, meta)

        // total 9005 points → k = ceil(9005 / 8000) = 2: every other point, plus the odd last one.
        assertEquals(2, record.strokes.size)
        val s = record.strokes[0]
        assertEquals(4502, s.pts.size)
        assertEquals(long.pts.last().ts, s.pts.last().ts)
        assertEquals(42.1111, s.pts.last().lat, 1e-9)
        assertEquals(0L, s.pts[0].ts); assertEquals(2L, s.pts[1].ts)
        assertEquals(listOf(1.0, 3.0), record.strokes[1].pts.map { it.lat })
    }

    @Test fun `haversine Boston to SF`() {
        val d = haversineKm(42.3601, -71.0589, 37.7749, -122.4194)
        assertTrue(d > 4330 && d < 4350)
    }

    @Test fun `geocoder names the nearest city`() {
        val cities = Cities.fromJson(javaClass.classLoader.getResource("cities.json")!!.readText())
        val geo = geocoder(cities)
        assertEquals("US", geo(42.3632761, -71.0643721)?.cc)
        assertEquals("Boston", geo(42.3632761, -71.0643721)?.name)
        assertNull(geo(0.0, -160.0))
    }
}
