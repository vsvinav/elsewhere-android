package com.elsewhere.app.crew

import com.elsewhere.app.ingest.Cities
import com.elsewhere.app.ingest.geocoder
import com.elsewhere.app.ingest.haversineKm
import com.elsewhere.app.ingest.record.geohash
import kotlin.math.cos
import kotlin.math.floor

/** A place a city-grade ping may claim: the listed city nearest the phone, if any within 50 km. */
data class CityPlace(val name: String, val lat: Double, val lng: Double)

class CityLookup(private val cities: Cities, private val maxKm: Double = 50.0) {
    private val geo = geocoder(cities)
    fun at(lat: Double, lng: Double): CityPlace? {
        val hit = geo(lat, lng)?.takeIf { it.km <= maxKm && it.name.isNotEmpty() } ?: return null
        var best = -1; var bd = Double.POSITIVE_INFINITY
        for (i in cities.lat.indices) if (cities.name[i] == hit.name) {
            val d = haversineKm(lat, lng, cities.lat[i], cities.lng[i]); if (d < bd) { bd = d; best = i }
        }
        return if (best < 0) null else CityPlace(hit.name, cities.lat[best], cities.lng[best])
    }
}

/** What the planner knows about me right now; `ts` in epoch seconds like the raw fixes. */
data class MyFix(val ts: Long, val lat: Double, val lng: Double, val habitat: Boolean, val absenceDays: Int?)

/**
 * Decides, per friend, whether a ping goes out now and what it says. Precise and sonar grades go every
 * 30 s, city every 5 min; a city position is the centroid nudged by a jitter seeded by the pair and the
 * city, so it never drifts while we stay in town and never points at the real spot.
 */
class PublishPlanner(private val cities: CityLookup?) {
    private val lastSent = HashMap<String, Long>()
    private var hitFor: Pair<String, Ping.Hit>? = null

    fun gradeFor(g: Grants): Grade? = when {
        g.livePrecise -> Grade.precise
        g.sonar -> Grade.sonar
        g.liveCity -> Grade.city
        else -> null
    }

    /** Queue a Sonar hit to ride on the next ping to this friend, which [plan] then sends at once. */
    fun carryHit(deviceId: String, hit: Ping.Hit) { hitFor = deviceId to hit }

    fun plan(fix: MyFix, friends: List<Friend>, session: ShareSession?, nowMs: Long): List<Pair<Friend, Ping>> {
        if (session == null || !session.active(nowMs)) return emptyList()
        val out = ArrayList<Pair<Friend, Ping>>()
        val place = cities?.at(fix.lat, fix.lng)
        for (f in friends) {
            if (f.deviceId !in session.friendIds) continue
            val grade = gradeFor(f.grants) ?: continue
            val hit = hitFor?.takeIf { it.first == f.deviceId }?.second
            val interval = if (grade == Grade.city) CITY_MS else LIVE_MS
            val last = lastSent[f.deviceId]
            if (hit == null && last != null && nowMs - last < interval) continue
            val (lat, lng) = when (grade) {
                Grade.precise -> fix.lat to fix.lng
                Grade.sonar -> cellCentre(fix.lat, fix.lng)
                Grade.city -> cityPoint(f.pairId, place, fix.lat, fix.lng)
            }
            out += f to Ping(t = fix.ts, grade = grade, lat = lat, lng = lng, city = place?.name, habitat = fix.habitat, absenceDays = fix.absenceDays, hit = hit)
            lastSent[f.deviceId] = nowMs
            if (hit != null) hitFor = null
        }
        return out
    }

    fun forget(deviceId: String) { lastSent.remove(deviceId) }
    fun reset() { lastSent.clear(); hitFor = null }

    companion object {
        const val LIVE_MS = 30_000L
        const val CITY_MS = 5 * 60_000L
        private const val JITTER_KM = 5.0

        /** Centre of the geohash-7 cell holding the point, about 150 m across. */
        fun cellCentre(lat: Double, lng: Double): Pair<Double, Double> {
            val h = geohash(lat, lng, 7)
            var latLo = -90.0; var latHi = 90.0; var lngLo = -180.0; var lngHi = 180.0
            var even = true
            for (ch in h) {
                val cd = "0123456789bcdefghjkmnpqrstuvwxyz".indexOf(ch)
                for (bit in 4 downTo 0) {
                    val on = (cd shr bit) and 1 == 1
                    if (even) { val mid = (lngLo + lngHi) / 2; if (on) lngLo = mid else lngHi = mid }
                    else { val mid = (latLo + latHi) / 2; if (on) latLo = mid else latHi = mid }
                    even = !even
                }
            }
            return round6((latLo + latHi) / 2) to round6((lngLo + lngHi) / 2)
        }

        /** The city centroid plus a jitter inside ±5 km that depends only on the pair and the city. */
        fun cityPoint(pairId: String, place: CityPlace?, lat: Double, lng: Double): Pair<Double, Double> {
            val name = place?.name ?: ("~" + floor(lat * 10) / 10 + "," + floor(lng * 10) / 10)
            val baseLat = place?.lat ?: (floor(lat * 10) / 10 + 0.05)
            val baseLng = place?.lng ?: (floor(lng * 10) / 10 + 0.05)
            val h = Crypto.sha256((pairId + "|" + name).toByteArray())
            val u1 = unit(h, 0); val u2 = unit(h, 4)
            val dLat = u1 * JITTER_KM / 111.32
            val dLng = u2 * JITTER_KM / (111.32 * cos(Math.toRadians(baseLat)).coerceAtLeast(0.01))
            return round6(baseLat + dLat) to round6(baseLng + dLng)
        }

        private fun unit(h: ByteArray, at: Int): Double {
            val v = ((h[at].toLong() and 0xff) shl 24) or ((h[at + 1].toLong() and 0xff) shl 16) or ((h[at + 2].toLong() and 0xff) shl 8) or (h[at + 3].toLong() and 0xff)
            return v / 4294967295.0 * 2 - 1
        }

        private fun round6(x: Double) = Math.round(x * 1e6) / 1e6
    }
}
