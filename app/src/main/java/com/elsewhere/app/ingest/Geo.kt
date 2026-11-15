package com.elsewhere.app.ingest

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// Port of pipeline/geo.py: great-circle distance and the offline nearest-city lookup.

fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = Math.PI / 180
    val p1 = lat1 * r; val p2 = lat2 * r; val dp = p2 - p1; val dl = (lng2 - lng1) * r
    val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 2 * 6371 * asin(sqrt(a))
}

/** Nearest city in a 1° grid; the table is GeoNames cities ≥ 15k people. */
@Serializable
class Cities(val lat: DoubleArray, val lng: DoubleArray, val name: List<String>, val cc: List<String>) {
    companion object {
        fun fromJson(text: String): Cities = Json.decodeFromString(serializer(), text)
    }
}

data class CityHit(val name: String, val cc: String, val km: Double = 0.0)

fun geocoder(c: Cities): (Double, Double) -> CityHit? {
    val grid = HashMap<Int, MutableList<Int>>()
    val key = { lat: Double, lng: Double -> (floor(lat).toInt() + 90) * 400 + (floor(lng).toInt() + 180) }
    for (i in c.lat.indices) grid.getOrPut(key(c.lat[i], c.lng[i])) { ArrayList() }.add(i)
    return { lat, lng ->
        var best = -1
        var bd = Double.POSITIVE_INFINITY
        // The nearest city can sit in the neighbouring cell, so always look one ring past the first hit.
        var ring = 0
        while (ring <= 3 && (best < 0 || ring < 2)) {
            for (dy in -ring..ring) for (dx in -ring..ring) {
                if (max(abs(dy), abs(dx)) != ring) continue
                for (i in grid[key(lat + dy, lng + dx)] ?: emptyList<Int>()) {
                    val d = haversineKm(lat, lng, c.lat[i], c.lng[i])
                    if (d < bd) { bd = d; best = i }
                }
            }
            ring++
        }
        if (best < 0) null else CityHit(c.name[best], c.cc[best], bd)
    }
}
