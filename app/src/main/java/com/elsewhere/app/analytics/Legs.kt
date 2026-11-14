package com.elsewhere.app.analytics

import com.elsewhere.app.data.Activity
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Path
import com.elsewhere.app.data.Record
import java.lang.ref.WeakReference
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// Port of web/src/analytics/legs.ts

/**
 * One drawn line per activity, in absolute seconds: the GPS points inside it, pinned to the leg's own start and end.
 * `ts` is fractional for a guessed great-circle arc, whose points are spread evenly over the flight.
 */
data class Leg(
    val path: List<LatLng>, val ts: DoubleArray, val mode: String, val lens: Lens,
    /** no GPS points: a straight guess between the two places */
    val guessed: Boolean,
)

/** Points along the great circle from a to b, so a flight bends the way it was flown instead of cutting straight across. */
fun greatCircle(a: LatLng, b: LatLng, n: Int): List<LatLng> {
    val r = Math.PI / 180
    val l1 = a.lng * r; val f1 = a.lat * r; val l2 = b.lng * r; val f2 = b.lat * r
    val d = 2 * asin(sqrt(sin((f2 - f1) / 2) * sin((f2 - f1) / 2) + cos(f1) * cos(f2) * sin((l2 - l1) / 2) * sin((l2 - l1) / 2)))
    if (d < 1e-9) return listOf(a, b)
    val out = ArrayList<LatLng>(n + 1)
    for (i in 0..n) {
        val f = i.toDouble() / n; val A = sin((1 - f) * d) / sin(d); val B = sin(f * d) / sin(d)
        val x = A * cos(f1) * cos(l1) + B * cos(f2) * cos(l2)
        val y = A * cos(f1) * sin(l1) + B * cos(f2) * sin(l2)
        val z = A * sin(f1) + B * sin(f2)
        out.add(LatLng(atan2(z, hypot(x, y)) / r, atan2(y, x) / r))
    }
    return out
}

private class LegCache(acts: List<Activity>, paths: List<Path>, val legs: List<Leg>) {
    val acts = WeakReference(acts); val paths = WeakReference(paths)
}
private var LEGS: LegCache? = null

/** legs[i] belongs to data.activities[i]. Path points outside every activity (jitter during a stay) are dropped. */
fun legsOf(data: Record): List<Leg> {
    LEGS?.let { if (it.acts.get() === data.activities && it.paths.get() === data.paths) return it.legs }
    val acts = data.activities; val paths = data.paths
    val legs = ArrayList<Leg>(acts.size)
    var pi = 0
    for (a in acts) {
        val path = arrayListOf(LatLng(a.sLat, a.sLng)); val ts = arrayListOf(a.start.toDouble())
        while (pi < paths.size && paths[pi].end < a.start) pi++
        var j = pi
        while (j < paths.size && paths[j].start <= a.end) {
            for (p in paths[j].pts) if (p.ts > a.start && p.ts < a.end) { path.add(LatLng(p.lat, p.lng)); ts.add(p.ts.toDouble()) }
            j++
        }
        val lens = lensOf(a.mode)
        if (path.size == 1 && lens == Lens.FLY && a.m > 80_000) {
            val arc = greatCircle(LatLng(a.sLat, a.sLng), LatLng(a.eLat, a.eLng), 48)
            val arcTs = DoubleArray(arc.size) { i -> a.start + ((a.end - a.start).toDouble() * i) / (arc.size - 1) }
            legs.add(Leg(arc, arcTs, a.mode, lens, guessed = false))
            continue
        }
        val guessed = path.size == 1
        path.add(LatLng(a.eLat, a.eLng)); ts.add(max(a.end.toDouble(), ts.last() + 1))
        legs.add(Leg(path, ts.toDoubleArray(), a.mode, lens, guessed))
    }
    LEGS = LegCache(acts, paths, legs)
    return legs
}
