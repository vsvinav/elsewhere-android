package com.elsewhere.app.analytics

import com.elsewhere.app.Fx
import com.elsewhere.app.Fx.D
import com.elsewhere.app.Fx.H
import com.elsewhere.app.Fx.act
import com.elsewhere.app.Fx.place
import com.elsewhere.app.Fx.visit
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Role
import com.elsewhere.app.data.Units
import com.elsewhere.app.data.filterData
import com.elsewhere.app.ui.nav.Door
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Twin of web/src/analytics/facts.test.ts. */
class FactsTest {
    private val JAN = Fx.utc(2025, 0)
    private fun pl(role: Role?, city: String, cc: String, lat: Double, lng: Double) =
        place(role, city, cc, 1, JAN, JAN, null, lat, lng, 1.0)

    // Four nights home, two in Paris (new), one home, two in Paris again (repeat); Jan 10 unaccounted for.
    private val data = Fx.record(
        visits = listOf(
            visit(JAN, JAN + 4 * D - 12 * H, "home", lat = 42.36, lng = -71.06),
            visit(JAN + 4 * D, JAN + 6 * D - 12 * H, "hotel", lat = 48.85, lng = 2.35),
            visit(JAN + 6 * D, JAN + 7 * D - 12 * H, "home", lat = 42.36, lng = -71.06),
            visit(JAN + 7 * D, JAN + 9 * D - 12 * H, "hotel2", lat = 48.86, lng = 2.34),
        ),
        activities = listOf(
            act(JAN + 4 * D - 10 * H, JAN + 4 * D - 2 * H, "FLYING", 5_500_000.0, sLat = 42.36, sLng = -71.06, eLat = 48.85, eLng = 2.35),
            act(JAN + 6 * D - 10 * H, JAN + 6 * D - 2 * H, "FLYING", 5_500_000.0, sLat = 48.85, sLng = 2.35, eLat = 42.36, eLng = -71.06),
            act(JAN + 2 * H, JAN + 3 * H, "WALKING", 40_000.0, sLat = 42.36, sLng = -71.06, eLat = 42.4, eLng = -71.1),
        ),
        places = mapOf(
            "home" to pl(Role.HOME, "Boston", "US", 42.36, -71.06),
            "hotel" to pl(null, "Paris", "FR", 48.85, 2.35),
            "hotel2" to pl(null, "Paris", "FR", 48.86, 2.34),
        ),
        home = LatLng(42.36, -71.06),
        range = JAN..(JAN + 10 * D),
    )

    @Test fun `bedStrip one night per local date, new vs repeat judged by city, gaps are none`() {
        val n = bedStrip(data)
        assertEquals("home home home home new new home repeat repeat none none", n.joinToString(" ") { it.kind.key })
        assertEquals("Paris", n[4].city)
    }

    @Test fun `bedStrip a cut range keeps firsts judged over the whole record`() {
        val n = bedStrip(data, (JAN + 7 * D)..(JAN + 8 * D))
        assertEquals(listOf(NightKind.REPEAT, NightKind.REPEAT), n.map { it.kind })
    }

    @Test fun `longestRun finds the longest matching streak with its dates`() {
        val n = bedStrip(data)
        assertEquals(Run("2025-01-01", "2025-01-04", 4), longestRun(n) { it.kind == NightKind.HOME })
        assertEquals(Run("2025-01-05", "2025-01-06", 2), longestRun(n) { it.kind == NightKind.NEW || it.kind == NightKind.REPEAT })
        assertNull(longestRun(n) { false })
    }

    @Test fun `facts fires on a small record, converts to miles, and stays silent on an empty one`() {
        val km = facts(data, Units.KM)
        val ids = km.map { it.id }
        assertTrue("hermit" in ids)
        assertTrue("furthest" in ids)
        assertTrue("walked" in ids)
        assertEquals("84 hours without leaving the house", km.first { it.id == "hermit" }.headline)
        // Sorted explored → record → scale → habit.
        assertTrue(km.indices.all { i -> i == 0 || km[i].kind.ordinal >= km[i - 1].kind.ordinal })

        val mi = facts(data, Units.MI)
        assertTrue(Regex("25 mi on foot").containsMatchIn(mi.first { it.id == "walked" }.detail))
        val fig = mi.first { it.id == "walked" }.figure
        assertEquals("mi", (fig as Figure.Scale).unit)

        assertEquals(emptyList<Fact>(), facts(data.copy(visits = emptyList(), activities = emptyList(), places = emptyMap()), Units.KM))
    }

    @Test fun `facts a cut range only counts what happened inside it, but keeps firsts from the whole record`() {
        val cut = filterData(data, (JAN + 7 * D)..(JAN + 10 * D))
        val f = facts(cut, Units.KM, data)
        assertFalse(f.any { it.id == "walked" })
        assertFalse(f.any { it.id == "hermit" })
        assertTrue(Regex("January 8, 2025").containsMatchIn(f.first { it.id == "furthest" }.detail))
    }

    private fun door(fs: List<Fact>, id: String) = fs.first { it.id == id }.door

    @Test fun `every fact opens a door that reads as its headline, and whole-record figures open on the whole window`() {
        val f = facts(data, Units.KM)
        assertTrue(f.all { it.door != null }, f.filter { it.door == null }.map { it.id }.toString())
        for (x in f) assertEquals(x.headline, x.door!!.label)
        assertEquals(Door.Range(JAN, JAN + 10 * D, "Walked a marathon"), door(f, "walked"))
        val home = facts(bigHome(), Units.KM)
        assertEquals(Door.Range(JAN, JAN + 40 * D, "100% of nights in your own bed"), door(home, "homebody"))
    }

    @Test fun `a record set at one place opens on that place and its day`() {
        val f = facts(data, Units.KM)
        assertEquals(Door.Place("hotel", JAN + 4 * D, "5,531 km from home"), door(f, "furthest"))
        assertEquals(Door.Place("hotel", JAN + 4 * D, "36 hours straight at Paris"), door(f, "longest-stay"))
        // Hermit opens on the stay itself, home to home.
        assertEquals(Door.Range(JAN, JAN + 4 * D - 12 * H, "84 hours without leaving the house"), door(f, "hermit"))
    }

    @Test fun `a streak opens on its nights, first midnight to the end of the last day`() {
        val streak = door(facts(bigHome(), Units.KM), "streak-home") as Door.Range
        assertEquals(Fx.utc(2025, 0, 1), streak.from)
        assertEquals(Fx.utc(2025, 1, 10) - 1, streak.to)
        assertEquals("40 nights in your own bed, straight", streak.label)
    }

    @Test fun `typed-place habits open on the places that made them`() {
        val cafes = (1..10).associate { "cafe$it" to place(null, "Boston", "US", 1, JAN, JAN, "Cafe $it", 42.36, -71.06, 1.0, primaryType = "cafe") }
        val f = facts(data.copy(places = data.places + cafes + ("bar1" to place(null, "Boston", "US", 2, JAN, JAN, "Bar", 42.36, -71.06, 6.0, primaryType = "bar"))), Units.KM)
        assertEquals(Door.Places(cafes.keys.toList(), "10 coffee runs · one every 1 days"), door(f, "coffee"))
        // A set of places stays a set, even a set of one.
        assertEquals(Door.Places(listOf("bar1"), f.first { it.id == "bars" }.headline), door(f, "bars"))
    }

    @Test fun `the fastest leg opens on the leg itself, and the beds you slept in open as pins`() {
        val drive = act(JAN + 2 * D, JAN + 2 * D + 2 * H, "IN_PASSENGER_VEHICLE", 200_000.0, sLat = 42.36, sLng = -71.06, eLat = 43.0, eLng = -72.0)
        // A second city slept in, so the beds are a figure.
        val lyon = visit(JAN + 9 * D, JAN + 10 * D - 12 * H, "lyon", lat = 45.76, lng = 4.84)
        val f = facts(data.copy(activities = data.activities + drive, visits = data.visits + lyon, places = data.places + ("lyon" to pl(null, "Lyon", "FR", 45.76, 4.84))), Units.KM)
        assertEquals(Door.Range(JAN + 2 * D, JAN + 2 * D + 2 * H, "100 km/h door to door"), door(f, "fastest"))
        assertTrue(f.first { it.id == "fastest" }.detail.contains(", by car."), "the mode reads by its short name")
        val density = door(f, "density") as Door.Places
        assertEquals(listOf("hotel", "hotel2", "lyon"), density.placeIds.sorted())
    }

    @Test fun `a fast leg nobody has a word for was travelled by road`() {
        val leg = act(JAN + 2 * D, JAN + 2 * D + 2 * H, "IN_VEHICLE", 200_000.0, sLat = 42.36, sLng = -71.06, eLat = 43.0, eLng = -72.0)
        val f = facts(data.copy(activities = data.activities + leg), Units.KM)
        assertTrue(f.first { it.id == "fastest" }.detail.contains(", by road."), f.first { it.id == "fastest" }.detail)
    }

    @Test fun `a city fact opens on every place the record names there, even ones with a role or none visited`() {
        val passes = (1..5).map { i -> visit(JAN + i * D + 10 * H, JAN + i * D + 11 * H, "cam$i", lat = 42.37, lng = -71.11) }
        val cam = (1..5).associate { "cam$it" to pl(null, "Cambridge", "US", 42.37, -71.11) }
        val extra = mapOf("cam-office" to pl(Role.WORK, "Cambridge", "US", 42.37, -71.11), "cam-quiet" to pl(null, "Cambridge", "US", 42.37, -71.11), "paris-desk" to pl(Role.WORK, "Paris", "FR", 48.85, 2.35))
        val f = facts(data.copy(visits = data.visits + passes, places = data.places + cam + extra), Units.KM)
        assertEquals((cam.keys + "cam-office" + "cam-quiet").sorted(), (door(f, "passed") as Door.Places).placeIds.sorted())
        assertEquals(listOf("hotel", "hotel2", "paris-desk"), (door(f, "second-city") as Door.Places).placeIds.sorted())
    }

    @Test fun `a loyal restaurant opens on it`() {
        val f = facts(data.copy(places = data.places + ("r" to place(null, "Boston", "US", 7, JAN, JAN, "Nook", 42.36, -71.06, 9.0, primaryType = "restaurant"))), Units.KM)
        assertEquals(Door.Place("r", label = "Nook, 7 times"), door(f, "regular"))
    }

    private fun bigHome() = Fx.record(
        visits = listOf(visit(JAN, JAN + 40 * D - 12 * H, "home", lat = 42.36, lng = -71.06)),
        places = mapOf("home" to pl(Role.HOME, "Boston", "US", 42.36, -71.06)),
        home = LatLng(42.36, -71.06), range = JAN..(JAN + 40 * D),
    )
}
