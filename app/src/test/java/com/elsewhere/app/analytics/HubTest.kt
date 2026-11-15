package com.elsewhere.app.analytics

import com.elsewhere.app.Fx
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Role
import com.elsewhere.app.ui.tabs.numbers.Peak
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Twin of web/src/analytics/hub.test.ts: same fixture, same expected values. */
class HubTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 0) = Fx.utc(y, m - 1, d) + h * Fx.H
    private fun v(start: Long, end: Long, placeId: String, lat: Double, lng: Double) = Fx.visit(start, end, placeId, lat = lat, lng = lng)
    private fun day(date: String, nNew: Int) = Fx.daily(date).copy(nNewPlaces = nNew, nVisits = 1, nPlaces = 1)

    // 2024: three nights home, then Paris for two nights and Lyon for one (new, new, new), home again.
    // 2025: home, two nights in Paris (repeat), then the record ends away.
    private val data = Fx.record(
        visits = listOf(
            v(at(2024, 1, 1), at(2024, 1, 4, 12), "home", 42.36, -71.06),
            v(at(2024, 1, 4, 20), at(2024, 1, 6, 12), "paris", 48.85, 2.35),
            v(at(2024, 1, 6, 14), at(2024, 1, 7, 12), "lyon", 45.76, 4.84),
            v(at(2024, 1, 7, 20), at(2024, 1, 9, 12), "home", 42.36, -71.06),
            v(at(2025, 3, 1), at(2025, 3, 2, 12), "home", 42.36, -71.06),
            v(at(2025, 3, 2, 20), at(2025, 3, 4, 12), "paris", 48.85, 2.35),
        ),
        activities = listOf(
            Fx.act(at(2024, 1, 4, 13), at(2024, 1, 4, 19), "FLYING", 5_500_000.0, -71.06, 42.36, 2.35, 48.85),
            Fx.act(at(2025, 3, 2, 13), at(2025, 3, 2, 19), "FLYING", 5_500_000.0, -71.06, 42.36, 2.35, 48.85),
            Fx.act(at(2025, 3, 3, 10), at(2025, 3, 3, 11), "WALKING", 4_000.0, 2.35, 48.85, 2.36, 48.86),
        ),
        places = mapOf(
            "home" to Fx.place(Role.HOME, "Boston", "US", lat = 42.36, lng = -71.06),
            "paris" to Fx.place(null, "Paris", "FR", lat = 48.85, lng = 2.35),
            "lyon" to Fx.place(null, "Lyon", "FR", lat = 45.76, lng = 4.84),
        ),
        // New places on three consecutive weeks of January 2024 (Mon 1, Mon 8, Mon 15), then one lone week in March 2025.
        daily = listOf(day("2024-01-04", 1), day("2024-01-10", 2), day("2024-01-16", 1), day("2024-01-30", 1), day("2025-03-03", 1)),
        home = LatLng(42.36, -71.06),
        range = at(2024, 1, 1)..at(2025, 3, 4, 12),
    )

    @Test fun `todaysFact picks the same fact for the same day and another on another day`() {
        val list = listOf("a", "b", "c", "d", "e", "f", "g")
        assertEquals(todaysFact(list, "2025-11-15"), todaysFact(list, "2025-11-15"))
        val picks = listOf("2025-11-15", "2025-11-16", "2025-11-17", "2025-11-18", "2025-11-19").map { todaysFact(list, it) }.toSet()
        assertTrue(picks.size > 1)
        assertNull(todaysFact(emptyList<String>(), "2025-11-15"))
        // The same hash as the web, so both platforms deal the same card on the same day.
        assertEquals(3932795612L, fnv("2025-11-15"))
    }

    @Test fun `streaks - longest run away, longest run of weeks with a new place, and the streak still running`() {
        val s = streaks(data)
        assertEquals(Run("2024-01-05", "2024-01-07", 3), s.away)
        assertEquals(Run("2024-01-01", "2024-01-21", 3), s.newWeeks)
        assertEquals(2, s.current)
    }

    @Test fun `streaks - a night with no record inside a run ends it, so the current streak never beats the longest`() {
        // Paris for three nights (new), one night unaccounted for, then two more in Paris (repeat) to the end.
        val gap = data.copy(
            visits = listOf(v(at(2024, 1, 1, 12), at(2024, 1, 4, 12), "paris", 48.85, 2.35), v(at(2024, 1, 5, 12), at(2024, 1, 7, 12), "paris", 48.85, 2.35)),
            meta = data.meta.copy(range = at(2024, 1, 1)..at(2024, 1, 7, 12)),
        )
        val s = streaks(gap)
        assertEquals(Run("2024-01-02", "2024-01-04", 3), s.away)
        assertEquals(2, s.current)
    }

    @Test fun `streaks - a record that ends at home has no current streak`() {
        val home = data.copy(visits = data.visits.take(4), meta = data.meta.copy(range = at(2024, 1, 1)..at(2024, 1, 9, 12)))
        assertEquals(0, streaks(home).current)
        assertEquals(Streaks(null, null, 0), streaks(data.copy(visits = emptyList(), daily = emptyList())))
    }

    @Test fun `compareYears - places, nights away, km and countries per calendar year`() {
        val ix = yearIndex(data)
        assertEquals(listOf(2024, 2025), yearsWithData(ix))
        val (a, b) = compareYears(ix, 2024, 2025)
        assertEquals(YearFacts(2024, 2, 3, 5500.0, 1), a)
        assertEquals(YearFacts(2025, 1, 2, 5504.0, 1), b)
        assertEquals(YearFacts(2023, 0, 0, 0.0, 0), compareYears(data, 2023, 2024).first)
        assertEquals(2024 to 2025, hubFigures(data).latestPair)
    }

    @Test fun `cycleYear steps to the next year with data, skipping the one on the other chip`() {
        assertEquals(2023, cycleYear(listOf(2022, 2023, 2024), 2022, 2024))
        assertEquals(2022, cycleYear(listOf(2022, 2023, 2024), 2023, 2024))
        assertEquals(2023, cycleYear(listOf(2023, 2024), 2023, 2024))
        assertEquals(2024, cycleYear(listOf(2024), 2024, 2024))
    }

    @Test fun `busiestHour names the hottest cell of the rhythm grid, or nothing on an empty one`() {
        val grid = List(7) { MutableList(24) { 0.0 } }
        assertNull(busiestHour(grid))
        grid[5][19] = 3.0; grid[1][8] = 2.0
        assertEquals(Peak(5, 19, 3.0), busiestHour(grid))
    }
}
