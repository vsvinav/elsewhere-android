package com.elsewhere.app.map

import com.elsewhere.app.analytics.distanceKm
import com.elsewhere.app.analytics.greatCircle
import com.elsewhere.app.analytics.homeOf
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.Trip
import java.lang.ref.WeakReference

// Port of the arc side of web/src/map/layers.ts: what the year draws instead of the trail.

/** One hop of a trip as a great circle: begun at `t0`, complete at `ts`, part of the trip that began at `trip`. */
class TripArc(val path: List<LatLng>, val t0: Double, val ts: Double, val km: Double, val trip: Double)

private const val ARC_POINTS = 48
private class ArcCache(val data: WeakReference<Record>, val arcs: List<TripArc>)
private var ARCS: ArcCache? = null

/** One arc per hop of every trip, trips in start order: home → anchor cities in order → home. Each hop takes an equal share of the trip's time; the last record asked about is kept. */
fun tripArcs(data: Record): List<TripArc> {
    ARCS?.let { if (it.data.get() === data) return it.arcs }
    return allArcs(data).also { ARCS = ArcCache(WeakReference(data), it) }
}

/** The arcs of trips begun by `t`; the whole list back when every trip has, so a frame at the end reuses it. */
fun List<TripArc>.begunBy(t: Double): List<TripArc> {
    var n = 0
    while (n < size && this[n].trip <= t) n++
    return if (n == size) this else subList(0, n)
}

private fun allArcs(data: Record): List<TripArc> {
    val cityPos = HashMap<String, LatLng>()
    fun posOf(city: String, trip: Trip): LatLng? {
        data.visits.firstOrNull { it.lvl == 0 && it.start >= trip.start && it.start <= trip.end && data.places[it.placeId]?.city == city }
            ?.let { return LatLng(it.lat, it.lng) }
        if (cityPos.isEmpty()) for (p in data.places.values) p.city?.let { c -> cityPos.putIfAbsent(c, LatLng(p.lat, p.lng)) }
        return cityPos[city]
    }
    val out = ArrayList<TripArc>()
    for (trip in data.trips.sortedBy { it.start }) {
        val h = homeOf(data, trip.start); val home = LatLng(h.lat, h.lng)
        val stops = listOf(home) + trip.anchorCities.mapNotNull { posOf(it, trip) } + home
        val n = stops.size - 1
        val span = (trip.end - trip.start).toDouble()
        for (i in 0 until n) {
            val a = trip.start + span * i / n; val b = if (i == n - 1) trip.end.toDouble() else trip.start + span * (i + 1) / n
            out.add(TripArc(greatCircle(stops[i], stops[i + 1], ARC_POINTS), a, b, distanceKm(stops[i], stops[i + 1]), trip.start.toDouble()))
        }
    }
    return out
}
