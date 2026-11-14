package com.elsewhere.app.analytics

import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Located
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Role
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Port of web/src/analytics/geo.ts

/** [[minLng, minLat], [maxLng, maxLat]] in the web. */
data class Bounds(val minLng: Double, val minLat: Double, val maxLng: Double, val maxLat: Double)

fun boundsOf(pts: List<Located>): Bounds? {
    if (pts.isEmpty()) return null
    var x0 = Double.POSITIVE_INFINITY; var y0 = Double.POSITIVE_INFINITY
    var x1 = Double.NEGATIVE_INFINITY; var y1 = Double.NEGATIVE_INFINITY
    for (p in pts) { x0 = min(x0, p.lng); y0 = min(y0, p.lat); x1 = max(x1, p.lng); y1 = max(y1, p.lat) }
    return Bounds(x0, y0, x1, y1)
}

fun isHome(p: Place?): Boolean = p?.role == Role.HOME
fun isWork(p: Place?): Boolean = p?.role == Role.WORK

/** Bounds of the dense part of a record (5th–95th percentile), so flights do not shrink daily life to a dot. */
fun denseBounds(pts: List<Located>): Bounds? {
    if (pts.isEmpty()) return null
    fun q(vals: List<Double>, p: Double): Double = vals.sorted()[floor(p * (vals.size - 1)).toInt()]
    val xs = pts.map { it.lng }; val ys = pts.map { it.lat }
    return Bounds(q(xs, 0.05), q(ys, 0.05), q(xs, 0.95), q(ys, 0.95))
}

fun Bounds.sw(): LatLng = LatLng(minLat, minLng)
fun Bounds.ne(): LatLng = LatLng(maxLat, maxLng)
