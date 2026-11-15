package com.elsewhere.app.ingest.record

import com.elsewhere.app.data.Pt
import com.elsewhere.app.ingest.PActivity
import com.elsewhere.app.ingest.PPath
import com.elsewhere.app.ingest.PVisit
import com.elsewhere.app.ingest.haversineKm
import kotlinx.serialization.Serializable

// Turns the phone's location stream into the same visits, activities and paths the Timeline parser
// produces, so the record never needs to know which of the two wrote a row. Pure Kotlin, no Android.

/** One location fix as the recorder sees it; `activityHint` is a raw Activity Recognition type. */
data class Fix(val ts: Long, val lat: Double, val lng: Double, val accM: Float, val speed: Float? = null, val activityHint: String? = null)

sealed interface Emission {
    val kind: String
    data class Visit(val visit: PVisit) : Emission { override val kind get() = "visit" }
    data class Activity(val activity: PActivity, val path: PPath) : Emission { override val kind get() = "activity" }
}

/** A stay in progress: a running centroid and the span of fixes seen inside its radius. */
@Serializable
data class Stay(val start: Long, val lastTs: Long, val lat: Double, val lng: Double, val n: Int) {
    val confirmed: Boolean get() = lastTs - start >= Segmenter.MIN_STAY_S
    fun absorb(f: Fix) = Stay(start, f.ts, lat + (f.lat - lat) / (n + 1), lng + (f.lng - lng) / (n + 1), n + 1)
}

@Serializable
data class SPt(val ts: Long, val lat: Double, val lng: Double, val speed: Float? = null, val hint: String? = null)

/** A leg in progress: where it set off from and the fixes since, already thinned for the path. */
@Serializable
data class Moving(val start: Long, val sLat: Double, val sLng: Double, val pts: List<SPt>)

/** Everything the recorder must keep across process death, in one serializable value. */
@Serializable
data class SegmenterState(val stay: Stay? = null, val moving: Moving? = null, val lastTs: Long? = null)

class Segmenter(state: SegmenterState = SegmenterState(), private val tzAt: (Long) -> Int) {
    var state: SegmenterState = state
        private set

    fun feed(f: Fix): List<Emission> {
        if (f.accM > MAX_ACC_M) return emptyList()
        val out = ArrayList<Emission>(2)
        val last = state.lastTs
        if (last != null && f.ts - last > GAP_S) {
            // The phone went dark: whatever was open can't be trusted past the last fix.
            state.stay?.takeIf { it.confirmed }?.let { out += visitOf(it) }
            state = SegmenterState()
        }
        val stay = state.stay
        var moving = state.moving
        when {
            stay == null -> {
                state = state.copy(stay = Stay(f.ts, f.ts, f.lat, f.lng, 1), moving = moving?.plus(f), lastTs = f.ts)
            }
            metres(stay.lat, stay.lng, f.lat, f.lng) <= RADIUS_M -> {
                // Until the stay is sure, its fixes also belong to the leg: a slow walk must not lose its trace.
                if (!stay.confirmed) moving = moving?.plus(f)
                val next = stay.absorb(f)
                if (next.confirmed && moving != null) {
                    // Settled: the leg that brought us here ends where this stay began.
                    out += activityOf(moving, next)
                    moving = null
                }
                state = state.copy(stay = next, moving = moving, lastTs = f.ts)
            }
            stay.confirmed -> {
                out += visitOf(stay)
                val departure = Moving(stay.lastTs, stay.lat, stay.lng, listOf(SPt(stay.lastTs, stay.lat, stay.lng))).plus(f)
                state = SegmenterState(stay = Stay(f.ts, f.ts, f.lat, f.lng, 1), moving = departure, lastTs = f.ts)
            }
            else -> {
                // A pause too short to count: it was part of the leg all along.
                val leg = (moving ?: Moving(stay.start, stay.lat, stay.lng, listOf(SPt(stay.start, stay.lat, stay.lng)))).plus(f)
                state = SegmenterState(stay = Stay(f.ts, f.ts, f.lat, f.lng, 1), moving = leg, lastTs = f.ts)
            }
        }
        return out
    }

    /** Closes a confirmed stay (end = its last fix) and forgets any half leg; for shutdown and stale sweeps. */
    fun close(): List<Emission> {
        val out = state.stay?.takeIf { it.confirmed }?.let { listOf(visitOf(it)) } ?: emptyList()
        state = SegmenterState(lastTs = state.lastTs)
        return out
    }

    private fun visitOf(s: Stay) = Emission.Visit(PVisit(
        id = s.start.toInt(), start = s.start, end = s.lastTs, tz = tzAt(s.start), placeId = devicePlaceId(s.lat, s.lng),
        lat = s.lat, lng = s.lng, type = "UNKNOWN", p = 1.0, lvl = 0,
    ))

    private fun activityOf(m: Moving, arrival: Stay): Emission.Activity {
        val end = arrival.start
        val pts = m.pts.filter { it.ts < end } + SPt(end, arrival.lat, arrival.lng)
        val metres = pts.zipWithNext().sumOf { (a, b) -> metres(a.lat, a.lng, b.lat, b.lng) }
        val activity = PActivity(
            id = m.start.toInt(), start = m.start, end = end, tz = tzAt(m.start), sLat = m.sLat, sLng = m.sLng, eLat = arrival.lat, eLng = arrival.lng,
            m = Math.rint(metres), mode = modeOf(pts, metres, end - m.start), p = 1.0,
        )
        return Emission.Activity(activity, PPath(m.start, end, pts.map { Pt(it.lat, it.lng, it.ts) }))
    }

    private fun Moving.plus(f: Fix): Moving {
        val prev = pts.last()
        val keep = f.ts - prev.ts >= MIN_GAP_S || metres(prev.lat, prev.lng, f.lat, f.lng) >= MIN_SPACING_M
        return if (keep) copy(pts = pts + SPt(f.ts, f.lat, f.lng, f.speed, f.activityHint)) else this
    }

    companion object {
        const val MIN_STAY_S = 5 * 60L
        const val RADIUS_M = 75.0
        const val MAX_ACC_M = 100f
        const val GAP_S = 2 * 3600L
        const val MIN_SPACING_M = 25.0
        const val MIN_GAP_S = 30L

        fun metres(lat1: Double, lng1: Double, lat2: Double, lng2: Double) = haversineKm(lat1, lng1, lat2, lng2) * 1000

        /** Timeline mode names: Activity Recognition's word for it when it spoke, else the speed. */
        fun modeOf(pts: List<SPt>, metres: Double, seconds: Long): String {
            val speeds = pts.mapNotNull { it.speed }.sorted()
            val mps = if (speeds.isNotEmpty()) speeds[speeds.size / 2].toDouble() else if (seconds > 0) metres / seconds else 0.0
            val kmh = mps * 3.6
            if (kmh > 180) return "FLYING"
            val hint = pts.mapNotNull { it.hint }.filter { it != "STILL" && it != "UNKNOWN" && it != "TILTING" }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            when (hint) {
                "WALKING", "ON_FOOT" -> return "WALKING"
                "RUNNING" -> return "RUNNING"
                "ON_BICYCLE" -> return "CYCLING"
                "IN_VEHICLE" -> return "IN_PASSENGER_VEHICLE"
            }
            return when {
                kmh > 25 -> "IN_PASSENGER_VEHICLE"
                kmh > 6 -> "CYCLING"
                else -> "WALKING"
            }
        }
    }
}
