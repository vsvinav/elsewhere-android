package com.elsewhere.app.ui.tabs.flights

import com.elsewhere.app.analytics.Port
import com.elsewhere.app.data.LatLng
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Orthographic globe math, a port of what d3-geo does for `geoOrthographic().rotate([-lng, -lat])`,
// kept free of Android types so it tests on the JVM. Angles in degrees at the API, radians inside.

private const val RAD = PI / 180
private const val DEG = 180 / PI
private const val EPS = 1e-12

/** The point of the earth facing the viewer. */
data class Rotation(val lng: Double, val lat: Double)

/** Where the sphere sits on the canvas: centre and radius in pixels. */
data class GlobeView(val cx: Double, val cy: Double, val r: Double)

/**
 * The unit vector of (lng, lat) after turning the sphere so `rot` faces the viewer: x toward the
 * viewer, y to the right, z up. This is d3's rotateLambda then rotatePhi, with gamma 0.
 */
fun rotate(lng: Double, lat: Double, rot: Rotation): DoubleArray {
    val l = (lng - rot.lng) * RAD; val f = lat * RAD
    val cf = cos(f)
    val x = cf * cos(l); val y = cf * sin(l); val z = sin(f)
    // Tilt about the y axis so the centre latitude comes to the front.
    val df = -rot.lat * RAD; val cd = cos(df); val sd = sin(df)
    return doubleArrayOf(x * cd - z * sd, y, z * cd + x * sd)
}

/** Canvas point of a rotated unit vector; the back hemisphere (x < 0) is nothing. */
private fun toScreen(v: DoubleArray, view: GlobeView): DoubleArray? =
    if (v[0] < -EPS) null else doubleArrayOf(view.cx + view.r * v[1], view.cy - view.r * v[2])

/** Orthographic projection of (lng, lat) onto the canvas, or null on the far side of the globe. */
fun project(lng: Double, lat: Double, rot: Rotation, view: GlobeView): DoubleArray? = toScreen(rotate(lng, lat, rot), view)

/** The great circle from `a` to `b` as n+1 [lng, lat] points, by spherical interpolation. */
fun greatCircle(a: LatLng, b: LatLng, n: Int = 64): List<DoubleArray> {
    val l1 = a.lng * RAD; val f1 = a.lat * RAD; val l2 = b.lng * RAD; val f2 = b.lat * RAD
    val p = doubleArrayOf(cos(f1) * cos(l1), cos(f1) * sin(l1), sin(f1))
    val q = doubleArrayOf(cos(f2) * cos(l2), cos(f2) * sin(l2), sin(f2))
    val d = acos((p[0] * q[0] + p[1] * q[1] + p[2] * q[2]).coerceIn(-1.0, 1.0))
    if (d < 1e-9) return List(n + 1) { doubleArrayOf(a.lng, a.lat) }
    // Antipodes have no single shortest path; go over the north pole, as a polar route would.
    if (PI - d < 1e-9) {
        val pole = LatLng(90.0, a.lng)
        val h = n / 2
        return greatCircle(a, pole, h) + greatCircle(pole, b, n - h).drop(1)
    }
    val sd = sin(d)
    return List(n + 1) { i ->
        val f = i.toDouble() / n
        val wa = sin((1 - f) * d) / sd; val wb = sin(f * d) / sd
        val x = wa * p[0] + wb * q[0]; val y = wa * p[1] + wb * q[1]; val z = wa * p[2] + wb * q[2]
        doubleArrayOf(atan2(y, x) * DEG, atan2(z, hypot(x, y)) * DEG)
    }
}

/** Where the segment from front point `p` to back point `q` crosses the horizon (x = 0), on the rim. */
private fun horizon(p: DoubleArray, q: DoubleArray): DoubleArray {
    val t = p[0] / (p[0] - q[0])
    val y = p[1] + t * (q[1] - p[1]); val z = p[2] + t * (q[2] - p[2])
    val n = hypot(y, z)
    return if (n < EPS) doubleArrayOf(0.0, 1.0, 0.0) else doubleArrayOf(0.0, y / n, z / n)
}

/**
 * A land ring cut to the front hemisphere, as flat [x, y, …] polygons ready to fill.
 *
 * d3 clips a polygon spherically, walking the horizon with the right winding so holes and
 * hemispheres come out exact. This is the simple cut it avoids: back-side points are dropped, each
 * visible run ends on the rim where its edge crosses the horizon, and the run is closed along the
 * shorter rim arc. A landmass covering more than half the horizon would close the wrong way round,
 * but no such shape exists at this scale, and the poster look forgives the rest.
 */
fun clipRing(ring: DoubleArray, rot: Rotation, view: GlobeView): List<FloatArray> {
    val n = ring.size / 2
    if (n < 3) return emptyList()
    // GeoJSON rings repeat their first point; drop the repeat so the ring is a plain cycle.
    val m = if (ring[0] == ring[(n - 1) * 2] && ring[1] == ring[(n - 1) * 2 + 1]) n - 1 else n
    val vs = Array(m) { i -> rotate(ring[i * 2], ring[i * 2 + 1], rot) }
    val front = BooleanArray(m) { vs[it][0] >= -EPS }
    val first = front.indexOfFirst { !it }
    if (first < 0) {
        val out = FloatArray(m * 2 + 2)
        for (i in 0 until m) { val s = toScreen(vs[i], view)!!; out[i * 2] = s[0].toFloat(); out[i * 2 + 1] = s[1].toFloat() }
        out[m * 2] = out[0]; out[m * 2 + 1] = out[1]
        return listOf(out)
    }
    if (front.none { it }) return emptyList()

    val runs = ArrayList<FloatArray>()
    val cur = ArrayList<Float>()
    fun add(v: DoubleArray) { val s = toScreen(v, view) ?: return; cur.add(s[0].toFloat()); cur.add(s[1].toFloat()) }
    var entry: DoubleArray? = null
    // Walk once around the cycle starting at a back-side point, so every run starts and ends on the rim.
    for (k in 1..m) {
        val i = (first + k) % m; val prev = (first + k - 1) % m
        if (front[i]) {
            if (!front[prev]) { entry = horizon(vs[i], vs[prev]); add(entry) }
            add(vs[i])
        } else if (front[prev]) {
            val exit = horizon(vs[prev], vs[i])
            add(exit)
            rimArc(exit, entry!!, view, cur)
            runs.add(cur.toFloatArray()); cur.clear()
        }
    }
    return runs
}

/** Points along the rim from `a` to `b` (both on the horizon), the shorter way round, closing on `b`. */
private fun rimArc(a: DoubleArray, b: DoubleArray, view: GlobeView, out: MutableList<Float>) {
    val ta = atan2(a[2], a[1]); val tb = atan2(b[2], b[1])
    var d = tb - ta
    if (d > PI) d -= 2 * PI
    if (d < -PI) d += 2 * PI
    val steps = max(1, (kotlin.math.abs(d) / (5 * RAD)).toInt())
    for (s in 1..steps) {
        val t = ta + d * s / steps
        out.add((view.cx + view.r * cos(t)).toFloat()); out.add((view.cy - view.r * sin(t)).toFloat())
    }
}

/**
 * Turn the globe so as many airports as possible sit on the lit side (within 75° of centre, weighted
 * by visits); among equal counts, the one that keeps them nearest the middle. A grid search is plenty.
 * Mirrors FlightMap.tsx; with nothing to show, the Atlantic faces out.
 */
fun bestCentre(ports: List<Port>): Rotation {
    if (ports.isEmpty()) return Rotation(0.0, 20.0)
    val limb = 75 * RAD
    val pts = ports.map { p -> doubleArrayOf(cos(p.p.lat * RAD) * cos(p.p.lng * RAD), cos(p.p.lat * RAD) * sin(p.p.lng * RAD), sin(p.p.lat * RAD), p.n.toDouble()) }
    var best = Rotation(ports[0].p.lng, ports[0].p.lat); var bestSeen = -1.0; var bestSpread = Double.POSITIVE_INFINITY
    var lat = -60
    while (lat <= 60) {
        var lng = -180
        while (lng < 180) {
            val cx = cos(lat * RAD) * cos(lng * RAD); val cy = cos(lat * RAD) * sin(lng * RAD); val cz = sin(lat * RAD)
            var seen = 0.0; var spread = 0.0
            for (q in pts) {
                val a = acos((cx * q[0] + cy * q[1] + cz * q[2]).coerceIn(-1.0, 1.0))
                if (a < limb) { seen += q[3]; spread += q[3] * a }
            }
            if (seen > bestSeen || (seen == bestSeen && spread < bestSpread)) { best = Rotation(lng.toDouble(), lat.toDouble()); bestSeen = seen; bestSpread = spread }
            lng += 5
        }
        lat += 5
    }
    return best
}

/** Index of the arc whose sampled screen points come nearest (x, y), if any within `reach` pixels. */
fun nearestArc(arcs: List<List<DoubleArray>>, x: Double, y: Double, reach: Double): Int? {
    var best: Int? = null; var bestD = reach
    arcs.forEachIndexed { i, pts ->
        for (p in pts) {
            val d = hypot(p[0] - x, p[1] - y)
            if (d < bestD) { bestD = d; best = i }
        }
    }
    return best
}

/** Rotation after a drag of (dx, dy) pixels over a globe of radius `r`: a full radius turns 90°. */
fun dragged(rot: Rotation, dx: Double, dy: Double, r: Double): Rotation {
    val k = 90.0 / max(1.0, r)
    var lng = rot.lng - dx * k
    while (lng > 180) lng -= 360
    while (lng < -180) lng += 360
    return Rotation(lng, min(90.0, max(-90.0, rot.lat + dy * k)))
}

/** Angular distance in degrees between two points on the sphere; used to cull labels at the limb. */
fun angleBetween(a: Rotation, lng: Double, lat: Double): Double {
    val s = sin(a.lat * RAD) * sin(lat * RAD) + cos(a.lat * RAD) * cos(lat * RAD) * cos((lng - a.lng) * RAD)
    return acos(s.coerceIn(-1.0, 1.0)) * DEG
}
