package com.elsewhere.app.analytics

import com.elsewhere.app.Fx
import com.elsewhere.app.Fx.place
import com.elsewhere.app.Fx.visit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Twin of web/src/world/flights.test.ts. */
class FlightsTest {
    private val airports = Airports.fromJson(javaClass.classLoader.getResource("airports.json")!!.readText())

    private fun act(s: Pair<Double, Double>, e: Pair<Double, Double>, mode: String = "FLYING", m: Double = 4_000_000.0) =
        Fx.act(0, 1, mode, m, sLng = s.first, sLat = s.second, eLng = e.first, eLat = e.second)

    private val data = Fx.record(
        activities = listOf(
            act(-71.0 to 42.3, -122.0 to 37.6), act(-122.0 to 37.6, -71.0 to 42.3), act(-71.0 to 42.3, 70.0 to 20.0),
            act(-71.0 to 42.3, -71.2 to 42.4, "FLYING", 20_000.0), act(-71.0 to 42.3, -122.0 to 37.6, "IN_PASSENGER_VEHICLE"),
        ),
        visits = listOf(visit(0, 1, "p", lng = -71.05, lat = 42.36)),
        places = mapOf("p" to place(city = "Boston")),
    )

    @Test fun `loads the airport table`() {
        assertEquals(154, airports.size)
        assertEquals(Airport("SFO", "San Francisco", 37.619, -122.375), airports[0])
    }

    @Test fun `merges return legs into one route, drops hops and ground legs, names ports by hub airport, else nearest visited city`() {
        val (hops, ports) = flights(data, airports)
        assertEquals(2, hops.size)
        assertEquals(2, hops[0].n)
        assertTrue(hops[1].km > 12_000)
        assertEquals(listOf("Boston", "San Francisco", ""), ports.map { it.name })
    }
}
