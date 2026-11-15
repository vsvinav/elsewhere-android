package com.elsewhere.app.map

import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Located
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min

// Port of web/src/map/camera.ts

/** A critically damped spring: `x` is where it sits, `v` how fast it is moving. */
class Spring(var x: Double, var v: Double = 0.0) {
    /** Step toward `target`; `period` is the ~settle time in seconds. */
    fun step(target: Double, dt: Double, period: Double) {
        val w = (2 * PI) / period
        // fixed substeps keep the integration stable and frame-rate independent
        var left = dt
        while (left > 0) {
            val h = min(0.016, left)
            val a = -2 * w * v - w * w * (x - target)
            v += a * h
            x += v * h
            left -= 0.016
        }
    }
}

/** Zoom that frames how fast you are moving (km/h), not the label Google gave the leg. */
fun zoomForKmh(kmh: Double): Double = when {
    kmh > 400 -> 5.0
    kmh > 120 -> 9.0
    kmh > 50 -> 11.5
    kmh > 18 -> 13.0
    kmh > 4 -> 14.5
    else -> 15.5
}

/** Replay pace in hours of record per real second, floored at a walking pace of 1 so slow replay is never "fast". */
fun paceOf(rate: Double): Double = max(1.0, rate / 3600 / 1.5)

/** How much slower the camera should settle as the replay speeds up: a day a second is ~2.6× calmer than real pace. */
fun calmFor(rate: Double): Double = 1 + log2(paceOf(rate)) * 0.4

/** Zoom ceiling by pace: street level at a stroll, region at a day a second, continent faster. */
fun zoomCapFor(rate: Double): Double = max(5.0, 15.5 - log2(paceOf(rate)) * 2.1)

/**
 * Cinematic zoom target: pull out at once (never lose the subject), but push in no faster than
 * [IN_PER_S] levels per second, slower the faster the replay, so a two-second stop never dives into town.
 */
private const val IN_PER_S = 1.4
fun approachZoom(cur: Double, want: Double, dt: Double, rate: Double): Double {
    if (want <= cur) return want
    return min(want, cur + (dt * IN_PER_S) / calmFor(rate))
}

/** Highest zoom at which both points fit inside `frac` of a viewport of the given pixel size. */
fun fitZoom(a: Located, b: Located, width: Double, height: Double, frac: Double = 0.55): Double {
    val k = cos(((a.lat + b.lat) / 2) * (PI / 180))
    // Mercator: a degree of longitude is a fixed pixel width; a degree of latitude stretches by 1/cos(lat)
    val dxDeg = abs(a.lng - b.lng); val dyDeg = abs(a.lat - b.lat) / max(0.2, k)
    val zx = log2((360 * width * frac) / max(1e-9, dxDeg * 256))
    val zy = log2((180 * height * frac) / max(1e-9, dyDeg * 256))
    return min(21.0, min(zx, zy))
}

/** Zoom that frames a cloud of recent positions in `frac` of the viewport; 15.5 when they all sit in one spot. */
fun dwellZoom(pts: List<Located>, width: Double, height: Double): Double {
    if (pts.size < 2) return 15.5
    var w = Double.POSITIVE_INFINITY; var e = Double.NEGATIVE_INFINITY; var s = Double.POSITIVE_INFINITY; var n = Double.NEGATIVE_INFINITY
    for (p in pts) { if (p.lng < w) w = p.lng; if (p.lng > e) e = p.lng; if (p.lat < s) s = p.lat; if (p.lat > n) n = p.lat }
    return min(15.5, fitZoom(LatLng(s, w), LatLng(n, e), width, height, 0.6))
}
