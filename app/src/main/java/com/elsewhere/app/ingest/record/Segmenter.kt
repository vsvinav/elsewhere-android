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
        // Fixes must arrive in order; a late or repeated one has nothing to add.
        if (state.lastTs?.let { f.ts <= it } == true) return emptyList()
        if (f.accM > MAX_ACC_M) {
            // Too coarse to place, but a coarse fix whose circle covers the open stay still says "still here".
            val stay = state.stay ?: return emptyList()
            if (metres(stay.lat, stay.lng, f.lat, f.lng) > RADIUS_M + f.accM) return emptyList()
            val next = stay.copy(lastTs = f.ts)
            val moving = state.moving
            if (next.confirmed && moving != null) {
                // Heartbeats alone made this a stay: the leg that brought us here is over too.
                state = state.copy(stay = next, moving = null, lastTs = f.ts)
                return listOf(activityOf(moving, next))
            }
            state = state.copy(stay = next, lastTs = f.ts)
            return emptyList()
        }
        val out = ArrayList<Emission>(2)
        val last = state.lastTs
        val stay0 = state.stay
        // Android stops delivering fixes to a phone that isn't moving, so a long silence at the same spot is the stay
        // going on, not the phone going dark.
        if (last != null && f.ts - last >= GAP_S && !(stay0 != null && metres(stay0.lat, stay0.lng, f.lat, f.lng) <= RADIUS_M)) {
            stay0?.takeIf { it.confirmed }?.let { out += visitOf(it) }
            // Dark for hours and now hundreds of kilometres away: a flight in airplane mode, not a hole in the record.
            val from = stay0?.let { SPt(last, it.lat, it.lng) } ?: state.moving?.pts?.lastOrNull()?.copy(ts = last)
            if (from != null && metres(from.lat, from.lng, f.lat, f.lng) >= JUMP_M) out += flightOf(from, f)
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
        val (mode, p) = modeOf(pts, metres, end - m.start)
        val activity = PActivity(
            id = m.start.toInt(), start = m.start, end = end, tz = tzAt(m.start), sLat = m.sLat, sLng = m.sLng, eLat = arrival.lat, eLng = arrival.lng,
            m = Math.rint(metres), mode = mode, p = p,
        )
        return Emission.Activity(activity, PPath(m.start, end, pts.map { Pt(it.lat, it.lng, it.ts) }))
    }

    /** The leg nobody saw: a great-circle hop from the last fix before the dark to the first after it. */
    private fun flightOf(from: SPt, f: Fix): Emission.Activity {
        val activity = PActivity(
            id = from.ts.toInt(), start = from.ts, end = f.ts, tz = tzAt(from.ts), sLat = from.lat, sLng = from.lng, eLat = f.lat, eLng = f.lng,
            m = Math.rint(metres(from.lat, from.lng, f.lat, f.lng)), mode = "FLYING", p = FLIGHT_GAP_P,
        )
        return Emission.Activity(activity, PPath(from.ts, f.ts, listOf(Pt(from.lat, from.lng, from.ts), Pt(f.lat, f.lng, f.ts))))
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
        /** A gap that lands this far away was a flight. */
        const val JUMP_M = 300_000.0
        const val FLIGHT_GAP_P = 0.8

        fun metres(lat1: Double, lng1: Double, lat2: Double, lng2: Double) = haversineKm(lat1, lng1, lat2, lng2) * 1000

        /**
         * Timeline mode names with how sure we are: Activity Recognition's word for it when it spoke, else the speed.
         * `IN_VEHICLE` cannot tell a car from a train, so a vehicle is a guess (0.6) unless the fast end of its speeds
         * says train (0.7); a hint on foot or on a bike is certain, a speed alone is a fair guess (0.8).
         */
        fun modeOf(pts: List<SPt>, metres: Double, seconds: Long): Pair<String, Double> {
            val speeds = pts.mapNotNull { it.speed }.sorted()
            val fallback = if (seconds > 0) metres / seconds else 0.0
            val mps = if (speeds.isNotEmpty()) speeds[speeds.size / 2].toDouble() else fallback
            val mps90 = if (speeds.isNotEmpty()) speeds[(speeds.size * 9 / 10).coerceAtMost(speeds.size - 1)].toDouble() else fallback
            val kmh = mps * 3.6
            val kmh90 = mps90 * 3.6
            if (kmh > 180) return "FLYING" to 1.0
            val hint = pts.mapNotNull { it.hint }.filter { it != "STILL" && it != "UNKNOWN" && it != "TILTING" }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            when (hint) {
                "WALKING", "ON_FOOT" -> return "WALKING" to 1.0
                "RUNNING" -> return "RUNNING" to 1.0
                "ON_BICYCLE" -> return "CYCLING" to 1.0
            }
            if (kmh90 > 140) return "IN_TRAIN" to 0.7
            if (hint == "IN_VEHICLE" || kmh > 25) return "IN_PASSENGER_VEHICLE" to 0.6
            return (if (kmh > 6) "CYCLING" else "WALKING") to 0.8
        }
    }
}
