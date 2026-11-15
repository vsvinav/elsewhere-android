package com.elsewhere.app.ui.tabs.flights

import com.elsewhere.app.analytics.Hop
import com.elsewhere.app.analytics.Port
import com.elsewhere.app.data.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlin.math.abs

// The "From <airport>" bill of web/src/tabs/Flights/Flights.tsx: one airport at a time, its routes
// and the airports they reach.

/** How many destinations the ledger prints before "+ N more". */
const val FROM_SHOWN = 8

/** Two runway coordinates within a tenth of a degree are the same airport. */
fun near(a: LatLng, b: LatLng): Boolean = abs(a.lng - b.lng) < 0.1 && abs(a.lat - b.lat) < 0.1

/** The airports a town was found for, in the order handed over (busiest first). */
fun namedPorts(ports: List<Port>): List<Port> = ports.filter { it.name.isNotEmpty() }

data class Dest(val name: String, val n: Int, val km: Double)

data class FromAirport(val from: Port, val hops: List<Hop>, val ports: List<Port>, val dests: List<Dest>) {
    val legs: Int get() = hops.sumOf { it.n }
    val km: Double get() = hops.sumOf { it.km * it.n }
    val shown: List<Dest> get() = dests.take(FROM_SHOWN)
    val more: Int get() = (dests.size - FROM_SHOWN).coerceAtLeast(0)
}

/**
 * The routes touching the `pick`th named airport, the airports at their ends, and the far end of
 * each as a destination, most flown first. A pick past the end means the last airport; no named
 * airport at all means null.
 */
fun fromAirport(hops: List<Hop>, ports: List<Port>, pick: Int, name: (LatLng) -> String): FromAirport? {
    val named = namedPorts(ports)
    if (named.isEmpty()) return null
    val from = named[pick.coerceIn(0, named.size - 1)]
    val fromHops = hops.filter { near(it.s, from.p) || near(it.t, from.p) }
    val fromPorts = ports.filter { p -> fromHops.any { near(it.s, p.p) || near(it.t, p.p) } }
    val dests = fromHops.map { h -> Dest(name(if (near(h.s, from.p)) h.t else h.s), h.n, h.km) }
        .sortedWith(compareByDescending<Dest> { it.n }.thenByDescending { it.km })
    return FromAirport(from, fromHops, fromPorts, dests)
}

/** An index that holds only as long as the data it was picked against; new data puts it back to 0. */
class ResettingPick<T : Any>(private val data: StateFlow<T?>) {
    private val state = MutableStateFlow<Pair<T?, Int>>(null to 0)
    val index: Flow<Int> = combine(data, state) { d, (against, i) -> if (d != null && d === against) i else 0 }
    fun set(i: Int) { state.value = data.value to i }
}
