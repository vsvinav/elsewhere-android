package com.elsewhere.app.ui.nav

import com.elsewhere.app.Fx
import com.elsewhere.app.analytics.facts
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Role
import com.elsewhere.app.ui.tabs.numbers.numbersUi
import com.elsewhere.app.ui.tabs.tour.tourUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

/** Doors are derived in one pass over a record; a six-figure record must not make the posters wait. */
class DoorPerfTest {
    private val start = Fx.utc(2015, 0, 1)
    private val record = run {
        val places = HashMap<String, com.elsewhere.app.data.Place>()
        places["home"] = Fx.place(Role.HOME, "Boston", "US", 50_000, start, start, "Home", 42.36, -71.06, 100_000.0)
        for (i in 0 until 20_000) {
            val city = "City ${i % 500}"
            places["p$i"] = Fx.place(null, city, "C${i % 50}", 5, start, start, "Place $i", 40.0 + (i % 500) * 0.01, -70.0 + (i / 500) * 0.01, 5.0, primaryType = if (i % 3 == 0) "cafe" else "restaurant")
        }
        val visits = (0 until 100_000).map { i ->
            val t = start + i * 2 * Fx.H
            if (i % 2 == 0) Fx.visit(t, t + Fx.H, "home", lat = 42.36, lng = -71.06) else Fx.visit(t, t + Fx.H, "p${i % 20_000}", lat = 40.0 + (i % 500) * 0.01, lng = -70.0 + (i % 20_000 / 500) * 0.01)
        }
        Fx.record(visits, places = places, home = LatLng(42.36, -71.06), range = start..(start + 100_000 * 2 * Fx.H))
    }

    @Test fun `a city index over twenty thousand places answers a hundred thousand city figures well inside a frame budget`() {
        lateinit var index: CityIndex
        val build = measureTimeMillis { index = CityIndex(record.places) }
        var n = 0
        val ask = measureTimeMillis { for (i in 0 until 100_000) n += index.ids("City ${i % 500}").size }
        assertEquals(100_000 * 40, n)
        assertTrue(build < 200 && ask < 200, "index $build ms, lookups $ask ms")
    }

    @Test fun `every door on a hundred-thousand-visit record is worked out once, in a single pass`() {
        val ms = measureTimeMillis {
            val tour = tourUi(record, record, null, null)
            val numbers = numbersUi(record, null)
            val fs = facts(record)
            assertEquals(numbers.stats.cities.size, numbers.doors.cities.size)
            assertEquals(tour.tour.stops.size, tour.doors.stops.size)
            assertTrue(tour.doors.thirdPlaces.size == 3 && fs.isNotEmpty() && fs.all { it.door != null }, fs.map { it.id to it.door }.toString())
        }
        assertTrue(ms < 5_000, "took $ms ms")
    }
}
