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
}
