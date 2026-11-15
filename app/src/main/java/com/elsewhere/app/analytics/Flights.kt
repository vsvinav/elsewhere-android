package com.elsewhere.app.analytics

import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Record
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.cos
import kotlin.math.hypot

// Port of web/src/world/flights.ts and world/airports.ts (the table lives in assets/airports.json).

/** Major airports; ~150 hubs hand-picked, extend when a flown port shows a town name instead of its city. */
@Serializable data class Airport(val code: String, val city: String, val lat: Double, val lng: Double)

object Airports {
    fun fromJson(text: String): List<Airport> = Json.decodeFromString(ListSerializer(Airport.serializer()), text)
}

data class Hop(val s: LatLng, val t: LatLng, val km: Double, var n: Int)
data class Port(val p: LatLng, val name: String, var n: Int)
data class Flights(val hops: List<Hop>, val ports: List<Port>)

/** Routes flown (return legs merged) and the airports they join, named by the nearest visited city. */
fun flights(data: Record, airports: List<Airport>): Flights {
    val hops = LinkedHashMap<String, Hop>(); val ports = LinkedHashMap<String, Port>()
    fun hubOf(lng: Double, lat: Double) = airports.firstOrNull { a -> hypot((a.lng - lng) * cos((lat * Math.PI) / 180), a.lat - lat) < 0.3 }
    // One dot per airport: a known hub keys by code, so legs that start on different runways still merge.
    fun key(p: LatLng) = hubOf(p.lng, p.lat)?.code ?: "${toFixed(p.lng, 1)},${toFixed(p.lat, 1)}"
    // The city most visited within ~100 km of the runway: the airport's town is rarely where you slept.
    fun nearCity(lng: Double, lat: Double): String {
        hubOf(lng, lat)?.let { return it.city }
        val n = LinkedHashMap<String, Int>()
        for (v in data.visits) {
            if (v.lvl != 0 || hypot((v.lng - lng) * cos((lat * Math.PI) / 180), v.lat - lat) > 1) continue
            val c = data.places[v.placeId]?.city ?: continue
            n[c] = (n[c] ?: 0) + 1
        }
        return n.entries.maxByOrNull { it.value }?.key ?: ""
    }
    for (a in data.activities) {
        if (a.mode != "FLYING" || a.m < 80_000) continue
        val s = LatLng(a.sLat, a.sLng); val t = LatLng(a.eLat, a.eLng)
        val k = listOf(key(s), key(t)).sorted().joinToString("|")
        val h = hops[k]
        if (h != null) h.n++ else hops[k] = Hop(s, t, distanceKm(s, t), 1)
        for (p in listOf(s, t)) {
            val pk = key(p); val port = ports[pk]
            if (port != null) port.n++ else ports[pk] = Port(p, nearCity(p.lng, p.lat), 1)
        }
    }
    return Flights(hops.values.toList(), ports.values.sortedByDescending { it.n })
}
