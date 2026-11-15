package com.elsewhere.app.ui.tabs.flights

import com.elsewhere.app.analytics.Hop
import com.elsewhere.app.analytics.Port
import com.elsewhere.app.data.LatLng
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FromAirportTest {
    private val bos = LatLng(42.36, -71.01); private val lis = LatLng(38.78, -9.14); private val nrt = LatLng(35.76, 140.39)
    private val sfo = LatLng(37.62, -122.38); private val nowhere = LatLng(-12.0, 44.0)
    // Legs land on nearby runways: Boston's second coordinate is 0.05° off, still the same airport.
    private val bos2 = LatLng(42.40, -71.05)
    private val hops = listOf(
        Hop(bos, lis, 5180.0, 3), Hop(bos2, nrt, 10_800.0, 2), Hop(sfo, bos, 4340.0, 2), Hop(sfo, lis, 9100.0, 1),
        Hop(bos, nowhere, 12_000.0, 1),
    )
    // Busiest first, as `flights()` hands them over; one runway has no town near it.
    private val ports = listOf(Port(bos, "Boston", 8), Port(lis, "Lisbon", 4), Port(sfo, "San Francisco", 3), Port(nrt, "Tokyo", 2), Port(nowhere, "", 1))
    private fun name(p: LatLng) = ports.firstOrNull { near(it.p, p) }?.name?.ifEmpty { null } ?: "somewhere"

    @Test fun `only named airports can be picked, busiest first`() {
        assertEquals(listOf("Boston", "Lisbon", "San Francisco", "Tokyo"), namedPorts(ports).map { it.name })
    }

    @Test fun `the picked airport's routes, airports and destinations, most flown first`() {
        val f = fromAirport(hops, ports, 0, ::name)!!
        assertEquals("Boston", f.from.name)
        assertEquals(4, f.hops.size)
        assertEquals(setOf("Boston", "Lisbon", "San Francisco", "Tokyo", ""), f.ports.map { it.name }.toSet())
        assertEquals(listOf("Lisbon", "Tokyo", "San Francisco", "somewhere"), f.dests.map { it.name })
        assertEquals(listOf(3, 2, 2, 1), f.dests.map { it.n })
        assertEquals(8, f.legs)
        assertEquals(3 * 5180.0 + 2 * 10_800.0 + 2 * 4340.0 + 12_000.0, f.km, 1e-9)
    }

    @Test fun `a pick past the end falls back to the last airport, no airports gives nothing`() {
        assertEquals("Tokyo", fromAirport(hops, ports, 99, ::name)!!.from.name)
        assertEquals(listOf("Boston"), fromAirport(hops, ports, 99, ::name)!!.dests.map { it.name })
        assertNull(fromAirport(hops, listOf(Port(nowhere, "", 1)), 0, ::name))
    }

    @Test fun `the destination ledger shows eight and counts the rest`() {
        val many = (0 until 11).map { i -> Hop(bos, LatLng(10.0 + i, 10.0), 1000.0 + i, 11 - i) }
        val manyPorts = listOf(Port(bos, "Boston", 11)) + (0 until 11).map { i -> Port(LatLng(10.0 + i, 10.0), "Town $i", 1) }
        val f = fromAirport(many, manyPorts, 0) { p -> manyPorts.first { near(it.p, p) }.name }!!
        assertEquals(8, f.shown.size)
        assertEquals(3, f.more)
        assertEquals("Town 0", f.shown.first().name)
    }

    @Test fun `near is a tenth of a degree either way`() {
        assertTrue(near(bos, bos2))
        assertTrue(!near(bos, LatLng(42.36, -70.9)))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `the pick goes back to the busiest airport when the data changes`() = runTest {
        val data = MutableStateFlow<String?>("one")
        val pick = ResettingPick(data)
        assertEquals(0, pick.index.first())
        pick.set(2)
        assertEquals(2, pick.index.first())
        data.value = "two"
        assertEquals(0, pick.index.first())
        pick.set(1)
        assertEquals(1, pick.index.first())
    }
}
