package com.elsewhere.app.map

import com.elsewhere.app.analytics.Grain
import com.elsewhere.app.analytics.SegmentKind
import com.elsewhere.app.analytics.positionAt
import com.elsewhere.app.analytics.segmentAt
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Record
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Port of web/src/map/useFollow.ts: the follow camera's state and one frame of it, without the map or the store.

private const val POS_PERIOD_S = 1.8
private const val ZOOM_PERIOD_S = 2.6
private const val MIN_ZOOM = 3.0
// Real seconds before a big leg at which the camera starts opening up for it; the pull-out itself happens in the last part of that window.
private const val LEAD_REAL_S = 3.0
private const val PULL_REAL_S = 1.8
// Real seconds of recent positions that decide how far in the camera may settle while you linger in one area.
private const val DWELL_REAL_S = 6.0

/**
 * Follow camera: centre springs to where you are; zoom springs to frame the whole current leg
 * (so a flight shows the arc, a walk shows the street), settling to a town view on stays.
 * Call [reset] when the user takes the camera; the next tick picks up from wherever it sits.
 */
class Follow {
    private class Springs(val lng: Spring, val lat: Spring, val zoom: Spring)
    private class Recent(val t: Long, val p: LatLng)
    private class Anticipation(val start: Long, var k: Double)

    private var springs: Springs? = null
    private var lastTarget = 0L
    private var zWant: Double? = null
    private val recent = ArrayDeque<Recent>()
    private var anticip: Anticipation? = null
    private var coarseKey = ""; private var coarse: Bounds? = null

    fun reset() { springs = null; zWant = null; recent.clear(); anticip = null; coarseKey = ""; coarse = null }

    /**
     * One frame: where the camera should be now, or null when it has not moved and was reported within the last half
     * second. `tMs` is a monotonic clock, `w`/`h` the viewport in px, `current` where the map sits before the spring starts.
     * At the year or the month the camera holds that span's content instead and only glides as the content changes.
     */
    fun tick(data: Record, now: Long, rate: Double, speed: Double, dt: Double, tMs: Long, w: Int, h: Int, current: Cam, grain: Grain = Grain.DAY): Cam? {
        val here = positionAt(data, now) ?: return null
        val key = grainKey(data, now.toDouble(), grain)
        if (key != coarseKey) { coarseKey = key; coarse = if (grain == Grain.DAY) null else grainBounds(data, now.toDouble(), grain) }
        coarse?.let { b ->
            val f = frameOf(b, w, h)
            val sp = springs ?: Springs(Spring(current.lng), Spring(current.lat), Spring(current.zoom.toDouble())).also { springs = it }
            sp.lng.step(f.center.lng, dt, POS_PERIOD_S); sp.lat.step(f.center.lat, dt, POS_PERIOD_S); sp.zoom.step(f.zoom, dt, ZOOM_PERIOD_S)
            zWant = f.zoom; recent.clear(); anticip = null
            return report(sp, tMs)
        }
        val seg = segmentAt(data, now)
        val nxt = segmentAt(data, seg.end + 2)
        val f = frameFor(seg, if (nxt.start > seg.start) nxt else null, here, now, rate, w, h)
        // Faster replay: a calmer camera and a lower zoom ceiling, so the subject is framed rather than chased.
        // The zoom target itself pulls out at once but pushes in slowly, so a brief stop never dives into town.
        val calm = calmFor(rate)
        // Linger in one area and the ceiling lifts to frame just that area, so a week in town at high speed still gets its close-up.
        recent.addLast(Recent(tMs, here))
        while (recent.isNotEmpty() && recent.first().t < tMs - (DWELL_REAL_S * 1000).toLong()) recent.removeFirst()
        val settled = if (tMs - recent.first().t > 2000) dwellZoom(recent.map { it.p }, w.toDouble(), h.toDouble()) else MIN_ZOOM
        val cap = max(zoomCapFor(rate), min(settled, zoomCapFor(rate) + 5))
        var want = min(cap, max(MIN_ZOOM, f.zoom)); var center = f.center
        // Look ahead a few real seconds: a flight or long drive coming up opens the frame now, so it never cuts in abruptly.
        val up = if (seg.kind == SegmentKind.ACTIVITY && seg.km >= BIG_KM) null else upcomingBig(data, now, speed, LEAD_REAL_S)
        if (up != null) {
            // Smoothstep over the last PULL_REAL_S, and never back off once begun, so the frame opens once and stays open.
            val x = min(1.0, max(0.0, 1 - up.inS / PULL_REAL_S)); val raw = x * x * (3 - 2 * x)
            val prev = anticip?.takeIf { it.start == up.seg.start }?.k ?: 0.0
            val k = max(prev, raw)
            anticip = Anticipation(up.seg.start, k)
            val big = up.seg
            val bigZoom = max(MIN_ZOOM, fitZoom(big.s, big.e, w * 0.72, h * 0.72))
            want = min(want, want + (bigZoom - want) * k)
            val mid = lerp(big.s, big.e, 0.5)
            center = lerp(center, mid, 0.3 * k)
        } else anticip?.let { if (it.start <= now) anticip = null }
        val zTarget = approachZoom(zWant ?: want, want, dt, rate).also { zWant = it }

        val sp = springs ?: Springs(Spring(current.lng), Spring(current.lat), Spring(current.zoom.toDouble())).also { springs = it }
        val posCalm = sqrt(calm)
        sp.lng.step(center.lng, dt, POS_PERIOD_S * posCalm); sp.lat.step(center.lat, dt, POS_PERIOD_S * posCalm)
        sp.zoom.step(zTarget, dt, ZOOM_PERIOD_S * calm)

        return report(sp, tMs)
    }

    /** The camera the springs sit at, or null when it has not moved and was reported within the last half second. */
    private fun report(sp: Springs, tMs: Long): Cam? {
        val moved = abs(sp.lng.v) + abs(sp.lat.v) > 1e-7 || abs(sp.zoom.v) > 1e-4
        if (!moved && tMs - lastTarget <= 500) return null
        lastTarget = tMs
        return Cam(sp.lat.x, sp.lng.x, sp.zoom.x.toFloat())
    }
}
