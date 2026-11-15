package com.elsewhere.app.map

import com.elsewhere.app.analytics.Leg
import com.elsewhere.app.analytics.Lens
import com.elsewhere.app.analytics.bearing
import com.elsewhere.app.analytics.legsOf
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Record
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// Port of the trail side of web/src/map/layers.ts and simplify.ts: what the replay draws behind the stamp.

private const val WAKE_REAL_S = 16.0
const val HEAD_REAL_S = 4.0
private const val MIN_WAKE_S = 3 * 3600.0
private const val MAX_WAKE_S = 30 * 86400.0

/** Record seconds of dotted wake behind the stamp: ~16 s of screen time, never under three hours nor over a month. */
fun wakeLength(rate: Double): Double = min(MAX_WAKE_S, max(MIN_WAKE_S, rate * WAKE_REAL_S))

/** A value that eases toward what is asked over `settleMs`, snapping when it has not been asked for a while. */
class Eased(private val settleMs: Long, private val staleMs: Long = settleMs) {
    private var shown = 0.0; private var at = 0L
    fun at(want: Double, tMs: Long): Double {
        if (shown == 0.0 || tMs - at > staleMs) shown = want
        else shown += (want - shown) * min(1.0, (tMs - at).toDouble() / settleMs)
        at = tMs
        return shown
    }
}

/** What the stamp shows: the kind of movement, a dot at rest, or fast-forward chevrons when the pace is too quick for a vehicle to matter. */
enum class Stamp { FLY, DRIVE, TRANSIT, FOOT, STILL, FF }

private const val STAMP_HOLD_MS = 350L
private const val FF_PACE = 8.0

/** The stamp only changes once the new kind has held for a beat, so fast replay does not strobe between icons. */
class StampHold {
    private var shown = Stamp.STILL; private var pending = Stamp.STILL; private var since = 0L
    fun kind(kind: Stamp, rate: Double, bigLeg: Boolean, tMs: Long): Stamp {
        val want = if (kind != Stamp.STILL && !bigLeg && paceOf(rate) >= FF_PACE) Stamp.FF else kind
        if (want != pending) { pending = want; since = tMs }
        if (want != shown && tMs - since >= STAMP_HOLD_MS) shown = want
        return shown
    }
}

fun stampOf(lens: Lens): Stamp = when (lens) { Lens.FLY -> Stamp.FLY; Lens.TRANSIT -> Stamp.TRANSIT; Lens.FOOT -> Stamp.FOOT; Lens.DRIVE, Lens.ALL -> Stamp.DRIVE }

/** Indices kept by Ramer–Douglas–Peucker at `tol` (same units as the points); endpoints always survive. */
fun simplifyIdx(pts: List<LatLng>, tol: Double): IntArray {
    if (pts.size <= 2) return IntArray(pts.size) { it }
    val keep = BooleanArray(pts.size); keep[0] = true; keep[pts.size - 1] = true
    val stack = ArrayDeque<IntArray>(); stack.addLast(intArrayOf(0, pts.size - 1))
    val t2 = tol * tol
    while (stack.isNotEmpty()) {
        val (a, b) = stack.removeLast()
        val ax = pts[a].lng; val ay = pts[a].lat; val bx = pts[b].lng; val by = pts[b].lat
        val dx = bx - ax; val dy = by - ay; val len2 = dx * dx + dy * dy
        var best = -1; var bestD = t2
        for (i in a + 1 until b) {
            val px = pts[i].lng; val py = pts[i].lat
            val t = if (len2 != 0.0) max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / len2)) else 0.0
            val ex = ax + dx * t - px; val ey = ay + dy * t - py
            val d = ex * ex + ey * ey
            if (d > bestD) { bestD = d; best = i }
        }
        if (best >= 0) { keep[best] = true; stack.addLast(intArrayOf(a, best)); stack.addLast(intArrayOf(best, b)) }
    }
    return (pts.indices).filter { keep[it] }.toIntArray()
}

/** Degrees of longitude per screen pixel at a zoom (Google 256px tiles). */
fun degPerPx(zoom: Int): Double = 360 / (256 * 2.0.pow(zoom))

/** One drawn line of the replay: a leg's points and times, in the ink of its kind of movement. */
class Trail(val path: List<LatLng>, val ts: DoubleArray, val lens: Lens, val guessed: Boolean) {
    val t0: Double get() = ts[0]
    val t1: Double get() = ts[ts.size - 1]
    /** The box the whole leg fits in, so a paused frame can skip legs off screen before projecting a point. */
    val box: Bounds by lazy { Bounds(path.minOf { it.lat }, path.minOf { it.lng }, path.maxOf { it.lat }, path.maxOf { it.lng }) }
}

/** Trails in record order, with the longest span kept so a window search knows how far back an enclosing leg can start. */
class Trails(private val list: List<Trail>) : List<Trail> by list {
    val maxSpan: Double = list.maxOfOrNull { it.t1 - it.t0 } ?: 0.0
}

/** Where the drawn trail's head is: the marker is placed from the same thinned points the canvas draws, so it never leads or trails the line. */
data class TrailHead(val lat: Double, val lng: Double, val heading: Double)

/** The record's trails, thinned per zoom and filtered per lens, cached so a frame costs lookups, not geometry. */
class ReplayTrails(data: Record) {
    val all = Trails(legsOf(data).map { l: Leg -> Trail(l.path, l.ts, l.lens, l.guessed) })
    private val byLens = HashMap<Lens, Trails>()
    private val simp = HashMap<String, Trails>()

    fun of(lens: Lens): Trails = byLens.getOrPut(lens) { if (lens == Lens.ALL) all else Trails(all.filter { it.lens == lens }) }

    /** Trails thinned to ~1.5 px at a whole-number zoom: GPS jitter is never drawn, and the canvas gets a fraction of the points when zoomed out. */
    fun simplified(lens: Lens, zoom: Double): Trails {
        val zb = max(2, min(16, zoom.roundToInt()))
        return simp.getOrPut("${lens.key}:$zb") {
            val tol = degPerPx(zb) * 1.5
            Trails(of(lens).map { t ->
                if (t.path.size <= 2) return@map t
                val idx = simplifyIdx(t.path, tol)
                if (idx.size == t.path.size) t else Trail(idx.map { t.path[it] }, DoubleArray(idx.size) { t.ts[idx[it]] }, t.lens, t.guessed)
            })
        }
    }
}

/**
 * Trails alive anywhere in [from, to]: binary search on start, then back past every leg that could still reach the
 * window given the longest span, since exports do hold a walk inside a drive and the walk starts later.
 */
fun activeTrails(trails: Trails, from: Double, to: Double): List<Trail> {
    var lo = 0; var hi = trails.size
    while (lo < hi) { val m = (lo + hi) ushr 1; if (trails[m].t0 <= to) lo = m + 1 else hi = m }
    val out = ArrayList<Trail>()
    val floor = from - trails.maxSpan
    var i = lo - 1
    while (i >= 0 && trails[i].t0 >= floor) { if (trails[i].t1 >= from) out.add(trails[i]); i-- }
    out.reverse()
    return out
}

fun trailHead(trails: List<Trail>, t: Double): TrailHead? {
    var lo = 0; var hi = trails.size - 1
    while (lo < hi) { val m = (lo + hi + 1) ushr 1; if (trails[m].t0 <= t) lo = m else hi = m - 1 }
    val tr = trails.getOrNull(lo) ?: return null
    if (tr.t0 > t || tr.t1 < t) return null
    val ts = tr.ts
    var i = 1
    while (i < ts.size - 1 && ts[i] < t) i++
    val p0 = tr.path[i - 1]; val p1 = tr.path[i]
    val k = if (ts[i] > ts[i - 1]) (t - ts[i - 1]) / (ts[i] - ts[i - 1]) else 1.0
    return TrailHead(p0.lat + (p1.lat - p0.lat) * k, p0.lng + (p1.lng - p0.lng) * k, bearing(p0, p1))
}
