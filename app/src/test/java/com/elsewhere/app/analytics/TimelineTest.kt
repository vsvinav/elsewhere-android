package com.elsewhere.app.analytics

import com.elsewhere.app.Fx
import com.elsewhere.app.Fx.D
import com.elsewhere.app.Fx.iso
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Role
import com.elsewhere.app.data.Trip
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimelineTest {
    private val home = Fx.place(role = Role.HOME, city = "Lisbon", cc = "PT", lat = 38.72, lng = -9.14, name = "Home")
    private val porto = Fx.place(city = "Porto", cc = "PT", lat = 41.15, lng = -8.61, name = "Porto")
    private val paris = Fx.place(city = "Paris", cc = "FR", lat = 48.86, lng = 2.35, name = "Paris")
    private val range = iso("2024-01-01T00:00:00Z")..iso("2024-02-03T00:00:00Z")
    private fun tiny(trips: List<Trip> = emptyList(), range: LongRange = this.range) = Fx.record(
        visits = listOf(
            Fx.visit(iso("2024-01-01T00:00:00Z"), iso("2024-01-05T08:00:00Z"), "home", lat = 38.72, lng = -9.14, id = 1),
            Fx.visit(iso("2024-01-05T12:00:00Z"), iso("2024-01-07T10:00:00Z"), "porto", lat = 41.15, lng = -8.61, id = 2),
            Fx.visit(iso("2024-01-07T14:00:00Z"), iso("2024-01-10T10:00:00Z"), "paris", lat = 48.86, lng = 2.35, id = 3),
            Fx.visit(iso("2024-01-10T14:00:00Z"), iso("2024-01-20T00:00:00Z"), "home", lat = 38.72, lng = -9.14, id = 4),
            Fx.visit(iso("2024-02-01T12:00:00Z"), iso("2024-02-02T10:00:00Z"), "porto", lat = 41.15, lng = -8.61, id = 5),
        ),
        activities = listOf(
            Fx.act(iso("2024-01-05T08:00:00Z"), iso("2024-01-05T12:00:00Z"), "IN_PASSENGER_VEHICLE", 300_000.0, -9.14, 38.72, -8.61, 41.15),
            Fx.act(iso("2024-01-07T10:00:00Z"), iso("2024-01-07T14:00:00Z"), "FLYING", 1_200_000.0, -8.61, 41.15, 2.35, 48.86),
            Fx.act(iso("2024-01-10T10:00:00Z"), iso("2024-01-10T14:00:00Z"), "FLYING", 1_450_000.0, 2.35, 48.86, -9.14, 38.72),
        ),
        places = mapOf("home" to home, "porto" to porto, "paris" to paris),
        trips = trips,
        home = LatLng(38.72, -9.14), range = range,
    )

    @Test fun `prefix arrays never decrease`() {
        val c = cumulative(tiny())
        assertTrue(c.ts.isNotEmpty())
        for (i in 1 until c.ts.size) {
            assertTrue(c.ts[i] >= c.ts[i - 1]); assertTrue(c.km[i] >= c.km[i - 1]); assertTrue(c.cities[i] >= c.cities[i - 1])
            assertTrue(c.nightsAway[i] >= c.nightsAway[i - 1]); assertTrue(c.flights[i] >= c.flights[i - 1])
        }
    }

    @Test fun `a city counts once and only flown legs are flights`() {
        val end = atPlayhead(cumulative(tiny()), iso("2024-02-03T00:00:00Z").toDouble())
        assertEquals(3, end.cities); assertEquals(2, end.flights); assertEquals(2950.0, end.km, 1.0)
    }

    @Test fun `zeros before the first event and exact on a boundary`() {
        val c = cumulative(tiny())
        assertEquals(Rail(0.0, 0, 0, 0, null), atPlayhead(c, iso("2023-12-01T00:00:00Z").toDouble()))
        assertEquals(2, atPlayhead(c, iso("2024-01-05T12:00:00Z").toDouble()).cities)
        assertEquals(1, atPlayhead(c, iso("2024-01-05T11:59:59Z").toDouble()).cities)
    }

    @Test fun `away nights come from the beds strip and land at their own midnight`() {
        val c = cumulative(tiny())
        assertEquals(0, atPlayhead(c, iso("2024-01-05T23:59:59Z").toDouble()).nightsAway)
        assertEquals(1, atPlayhead(c, iso("2024-01-06T00:00:00Z").toDouble()).nightsAway)
        assertEquals(5, atPlayhead(c, iso("2024-01-10T23:00:00Z").toDouble()).nightsAway)
    }

    @Test fun `one giant leg emits every km mark it crosses`() {
        val far = Fx.record(
            activities = listOf(Fx.act(iso("2024-01-02T00:00:00Z"), iso("2024-01-03T00:00:00Z"), "FLYING", 21_000_000.0, -9.14, 38.72, 151.21, -33.87)),
            range = range,
        )
        assertEquals(listOf("10,000 km so far", "20,000 km so far"), cumulative(far).milestones.map { it.text })
    }

    @Test fun `a milestone fires once and lingers for two percent of the range`() {
        val c = cumulative(tiny())
        val first = c.milestones.single { it.text == "your first city" }
        val span = (range.last - range.first).toDouble()
        assertEquals("your first city", atPlayhead(c, first.ts).milestone)
        assertEquals("your first city", atPlayhead(c, first.ts + span * 0.019).milestone)
        assertNull(atPlayhead(c, first.ts + span * 0.021).milestone)
    }

    @Test fun `grain by span`() {
        assertEquals(Grain.YEAR, grainOf(401L * 86400)); assertEquals(Grain.MONTH, grainOf(400L * 86400))
        assertEquals(Grain.MONTH, grainOf(22L * 86400)); assertEquals(Grain.DAY, grainOf(21L * 86400))
    }

    @Test fun `a short window only has days`() {
        val (grain, allowed) = resolveGrain(tiny(range = range.first..range.first + 10 * D), Grain.YEAR)
        assertEquals(Grain.DAY, grain); assertEquals(listOf(Grain.DAY), allowed)
    }

    @Test fun `without trips the year has nothing to show, so a pick of it falls back to the span`() {
        val (grain, allowed) = resolveGrain(tiny(), Grain.YEAR)
        assertEquals(Grain.MONTH, grain); assertEquals(listOf(Grain.MONTH, Grain.DAY), allowed)
        assertEquals(Grain.DAY, resolveGrain(tiny(), Grain.DAY).first)
    }

    @Test fun `trips open every grain and an allowed pick wins`() {
        val trip = Trip(iso("2024-01-05T12:00:00Z"), iso("2024-01-10T10:00:00Z"), 5, 1450.0, emptyMap(), listOf("Porto", "Paris"), null)
        val (grain, allowed) = resolveGrain(tiny(trips = listOf(trip)), Grain.YEAR)
        assertEquals(Grain.YEAR, grain); assertEquals(Grain.entries, allowed)
        assertEquals(Grain.MONTH, resolveGrain(tiny(trips = listOf(trip)), null).first)
    }
}
