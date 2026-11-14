package com.elsewhere.app.analytics

import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Located
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.Visit
import com.elsewhere.app.ingest.haversineKm
import java.lang.ref.WeakReference
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Port of web/src/analytics/position.ts

/** Where you are and how you are moving at time t. mode is 'STILL' inside a visit. */
data class Whereabouts(override val lat: Double, override val lng: Double, val mode: String, val heading: Double) : Located

/** Degrees clockwise from north, a → b. */
fun bearing(a: Located, b: Located): Double {
    val r = Math.PI / 180; val f1 = a.lat * r; val f2 = b.lat * r; val dl = (b.lng - a.lng) * r
    val y = sin(dl) * cos(f2); val x = cos(f1) * sin(f2) - sin(f1) * cos(f2) * cos(dl)
    return ((atan2(y, x) / r) + 360) % 360
}

private class TopCache(visits: List<Visit>, val top: List<Visit>) { val visits = WeakReference(visits) }
private var TOP: TopCache? = null

/** Index of the last item with start <= t, or -1. Items are sorted by start. */
private fun lastStarted(items: List<com.elsewhere.app.data.Spanned>, t: Long): Int = lastLE(items.size, { i -> items[i].start.toDouble() }, t.toDouble())
private inline fun lastLE(n: Int, at: (Int) -> Double, t: Double): Int {
    var lo = 0; var hi = n - 1; var out = -1
    while (lo <= hi) { val m = (lo + hi) ushr 1; if (at(m) <= t) { out = m; lo = m + 1 } else hi = m - 1 }
    return out
}
private fun topVisits(data: Record): List<Visit> {
    TOP?.let { if (it.visits.get() === data.visits) return it.top }
    val top = data.visits.filter { it.lvl == 0 }
    TOP = TopCache(data.visits, top)
    return top
}

fun whereaboutsAt(data: Record, t: Long): Whereabouts? {
    val visits = topVisits(data)
    val vi = lastStarted(visits, t)
    if (vi >= 0 && t <= visits[vi].end) { val v = visits[vi]; return Whereabouts(v.lat, v.lng, "STILL", 0.0) }
    val ai = lastStarted(data.activities, t)
    if (ai >= 0 && t <= data.activities[ai].end) {
        // Same line the map draws, so the marker sits exactly on the head of the trail.
        val leg = legsOf(data)[ai]; val path = leg.path; val ts = leg.ts
        val k = min(ts.size - 1, max(1, lastLE(ts.size, { i -> ts[i] }, t.toDouble()) + 1))
        val p0 = path[k - 1]; val p1 = path[k]
        val f = min(1.0, max(0.0, (t - ts[k - 1]) / max(1.0, ts[k] - ts[k - 1])))
        return Whereabouts(p0.lat + (p1.lat - p0.lat) * f, p0.lng + (p1.lng - p0.lng) * f, leg.mode, bearing(p0, p1))
    }
    if (vi >= 0) { val v = visits[vi]; return Whereabouts(v.lat, v.lng, "STILL", 0.0) }
    return null
}

enum class SegmentKind { VISIT, ACTIVITY, GAP }
data class Segment(val kind: SegmentKind, val start: Long, val end: Long, val s: LatLng, val e: LatLng, val mode: String, val km: Double)

/** The visit, activity or gap the playhead is inside, with both ends so a camera can frame it. */
fun segmentAt(data: Record, t: Long): Segment {
    var prev: LatLng? = null; var nextStart: Long? = null; var nextP: LatLng? = null; var prevEnd: Long? = null
    for (v in data.visits) {
        if (v.lvl != 0) continue
        if (v.start > t) { nextStart = v.start; nextP = LatLng(v.lat, v.lng); break }
        val p = LatLng(v.lat, v.lng)
        if (t <= v.end) return Segment(SegmentKind.VISIT, v.start, v.end, p, p, "STILL", 0.0)
        if (prevEnd == null || v.end > prevEnd) { prevEnd = v.end; prev = p }
    }
    for (a in data.activities) {
        if (a.start > t) { if (nextStart == null || a.start < nextStart) { nextStart = a.start; nextP = LatLng(a.sLat, a.sLng) }; break }
        val s = LatLng(a.sLat, a.sLng); val e = LatLng(a.eLat, a.eLng)
        if (t <= a.end) return Segment(SegmentKind.ACTIVITY, a.start, a.end, s, e, a.mode, a.m / 1000)
        if (prevEnd == null || a.end > prevEnd) { prevEnd = a.end; prev = e }
    }
    val s = prev ?: nextP ?: data.profile.home; val e = nextP ?: s
    val start = prevEnd ?: min(t, data.meta.range.first)
    return Segment(SegmentKind.GAP, start, nextStart ?: (t + 6 * 3600), s, e, "GAP", distanceKm(s, e))
}

fun positionAt(data: Record, t: Long): LatLng? = whereaboutsAt(data, t)?.let { LatLng(it.lat, it.lng) }

fun distanceKm(a: Located, b: Located): Double = haversineKm(a.lat, a.lng, b.lat, b.lng)
