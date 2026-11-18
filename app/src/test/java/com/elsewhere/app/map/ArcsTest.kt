package com.elsewhere.app.map

import com.elsewhere.app.Fx
import com.elsewhere.app.Fx.D
import com.elsewhere.app.Fx.place
import com.elsewhere.app.Fx.visit
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Trip
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArcsTest {
    private val home = LatLng(51.5, -0.1)
    private val porto = LatLng(41.15, -8.61); private val lisbon = LatLng(38.72, -9.14); private val madrid = LatLng(40.42, -3.70)
    private val t1 = Fx.utc(2024, 2, 10); private val t2 = Fx.utc(2024, 7, 1)
    private fun trip(start: Long, end: Long, cities: List<String>) = Trip(start, end, 3, 1000.0, emptyMap(), cities, null)
    // The second trip is listed first, so the arcs must sort; Lisbon has no visit in its trip and falls back to its place.
    private val data = Fx.record(
        trips = listOf(trip(t2, t2 + 6 * D, listOf("Lisbon", "Madrid")), trip(t1, t1 + 4 * D, listOf("Porto"))),
        visits = listOf(
            visit(t1 + D, t1 + 3 * D, "porto-hotel", lng = porto.lng, lat = porto.lat),
            visit(t2 + 3 * D, t2 + 5 * D, "madrid-flat", lng = madrid.lng, lat = madrid.lat),
        ),
        places = mapOf(
            "porto-hotel" to place(city = "Porto", lat = porto.lat, lng = porto.lng),
            "lisbon-bar" to place(city = "Lisbon", lat = lisbon.lat, lng = lisbon.lng),
            "madrid-flat" to place(city = "Madrid", lat = madrid.lat, lng = madrid.lng),
        ),
        home = home, range = t1..(t2 + 6 * D),
    )

    private fun near(want: LatLng, got: LatLng) { assertEquals(want.lat, got.lat, 1e-6); assertEquals(want.lng, got.lng, 1e-6) }

    @Test fun `one hop per leg of each trip, home to each anchor and back, sorted by trip start`() {
        val arcs = tripArcs(data)
        assertEquals(5, arcs.size)
        assertEquals(listOf(t1, t1, t2, t2, t2).map { it.toDouble() }, arcs.map { it.trip })
        near(home, arcs[0].path.first()); near(porto, arcs[0].path.last())
        near(porto, arcs[1].path.first()); near(home, arcs[1].path.last())
        near(lisbon, arcs[2].path.last()); near(madrid, arcs[3].path.last()); near(home, arcs[4].path.last())
        assertTrue(arcs.all { it.path.size == 49 })
    }

    @Test fun `each hop takes an equal share of its trip and ends when the next begins`() {
        val arcs = tripArcs(data)
        assertEquals(t1.toDouble(), arcs[0].t0, 1e-9); assertEquals(t1 + 2.0 * D, arcs[0].ts, 1e-9)
        assertEquals(t1 + 2.0 * D, arcs[1].t0, 1e-9); assertEquals(t1 + 4.0 * D, arcs[1].ts, 1e-9)
        assertEquals(t2 + 6.0 * D, arcs[4].ts, 1e-9)
        assertEquals(1321.0, arcs[0].km, 5.0)
    }

    @Test fun `only trips begun by the playhead are shown, and the full list comes back as is`() {
        val all = tripArcs(data)
        assertTrue(all.begunBy(t1 - 1.0).isEmpty())
        assertEquals(2, all.begunBy(t1 + 1.0).size)
        assertEquals(2, all.begunBy(t2 - 1.0).size)
        assertSame(all, all.begunBy(t2.toDouble()))
    }

    @Test fun `the same record yields the same arcs without recomputing`() {
        assertSame(tripArcs(data), tripArcs(data))
        assertEquals(0, tripArcs(data.copy(trips = emptyList())).size)
    }
}
