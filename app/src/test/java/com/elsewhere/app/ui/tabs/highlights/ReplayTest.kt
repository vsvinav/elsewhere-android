package com.elsewhere.app.ui.tabs.highlights

import com.elsewhere.app.OnThisDay
import com.elsewhere.app.OnThisDay.at
import com.elsewhere.app.data.Path
import com.elsewhere.app.data.Pt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Twin of web/src/world/TripMap.test.ts: the route clipped to a playhead. */
class ReplayTest {
    private val data = OnThisDay.data().let { d ->
        d.copy(strokes = listOf(Path(at(2023, 11, 12, 10), at(2023, 11, 12, 11), (0..6).map { i -> Pt(35 + i / 100.0, 135.7, at(2023, 11, 12, 10, i * 10)) })))
    }
    private val range = at(2023, 11, 11, 8)..at(2023, 11, 19, 2)
    private val g = routeGeometry(data, range)

    @Test fun `keeps the whole trip's legs so the fit does not jump while playing`() {
        assertEquals(2, g.flights.size); assertEquals(1, g.roads.size)
    }
    @Test fun `draws nothing before the first leg and everything at the end`() {
        val before = routeAt(g, at(2023, 11, 11, 7))
        assertEquals(0, before.flights.size); assertEquals(0, before.roads.size)
        val end = routeAt(g, range.last)
        assertEquals(g.flights, end.flights)
        assertEquals(7, end.roads[0].pts.size)
    }
    @Test fun `interpolates a flight in progress and trims a road to the points driven so far`() {
        val mid = routeAt(g, at(2023, 11, 11, 15))
        assertEquals(1, mid.flights.size)
        assertEquals((42.36 + 35.01) / 2, mid.flights[0].eLat, 0.05)
        val road = routeAt(g, at(2023, 11, 12, 10, 30))
        assertEquals(4, road.roads[0].pts.size)
    }
    @Test fun `is the whole route when there is no playhead`() {
        assertSame(g, routeAt(g, null))
    }
}
