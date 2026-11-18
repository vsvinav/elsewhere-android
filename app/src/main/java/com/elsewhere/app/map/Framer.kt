package com.elsewhere.app.map

import androidx.compose.ui.unit.IntSize
import com.elsewhere.app.analytics.Grain
import com.elsewhere.app.data.Located
import com.elsewhere.app.data.Record
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * "Frame these bounds" needs the view's size in px, which Compose reports only after layout. A fit asked before
 * then waits for the first size and runs once; a later size change (rotation) leaves the camera where it is.
 * Every fit glides rather than jumps, so the user sees where the pins went. Zoom counts 256 logical px per
 * tile and Compose reports physical px, so size and padding are scaled back to dp before solving.
 */
class Framer(private val go: (bounds: Bounds, sizePx: IntSize, paddingPx: Int) -> Unit) {
    /** The Mercator solve, for a host whose camera is set by hand. */
    constructor(move: (cam: Cam, animateMs: Int) -> Unit) : this({ b, size, pad -> move(camForBounds(b, size.width, size.height, pad), ANIMATE_MS) }) {
        solveInDp = true
    }

    private var solveInDp = false
    var density = 1f
    var size: IntSize = IntSize.Zero
        set(v) {
            field = v
            if (v != IntSize.Zero) pending?.let { pending = null; it() }
        }
    private var pending: (() -> Unit)? = null

    fun fit(bounds: Bounds, paddingPx: Int) {
        val run = {
            if (solveInDp) go(bounds, IntSize((size.width / density).roundToInt(), (size.height / density).roundToInt()), (paddingPx / density).roundToInt())
            else go(bounds, size, paddingPx)
        }
        if (size != IntSize.Zero) run() else pending = run
    }

    companion object { const val ANIMATE_MS = 400 }
}

// ---- What a coarser grain holds in frame; port of grainBounds / monthOf / grainKey in web/src/map/frame.ts ----

/**
 * What the year or the month should hold in frame at `now`: the year shows the trip under the playhead, or every
 * trip when none is; the month shows that calendar month's stops. The day frames nothing extra (the follow-cam does).
 */
fun grainBounds(data: Record, now: Double, grain: Grain): Bounds? = when (grain) {
    Grain.YEAR -> {
        val cur = tripUnder(data, now)
        val arcs = if (cur != null) tripArcs(data).filter { it.trip == cur.toDouble() } else tripArcs(data)
        boundsAround(arcs.flatMap { it.path })
    }
    Grain.MONTH -> {
        val m = monthOf(now.toLong())
        boundsAround(data.visits.filter { it.lvl == 0 && it.end >= m.first && it.start <= m.last })
    }
    Grain.DAY -> null
}

/** The UTC calendar month holding `now`, as [start, end) epoch seconds (`last` is the second before `end`). */
fun monthOf(now: Long): LongRange {
    val u = Instant.ofEpochSecond(now).atOffset(ZoneOffset.UTC)
    val a = LocalDate.of(u.year, u.monthValue, 1)
    return a.atStartOfDay(ZoneOffset.UTC).toEpochSecond() until a.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
}

/** Changes exactly when [grainBounds] would: the trip under the playhead, or the calendar month. */
fun grainKey(data: Record, now: Double, grain: Grain): String = when (grain) {
    Grain.YEAR -> "year:${tripUnder(data, now) ?: "all"}"
    Grain.MONTH -> "month:${monthOf(now.toLong()).first}"
    Grain.DAY -> "day"
}

/** The start of the trip the playhead is inside, if any. */
private fun tripUnder(data: Record, now: Double): Long? = data.trips.firstOrNull { it.start <= now && now <= it.end }?.start

private fun boundsAround(pts: List<Located>): Bounds? =
    if (pts.isEmpty()) null else Bounds(pts.minOf { it.lat }, pts.minOf { it.lng }, pts.maxOf { it.lat }, pts.maxOf { it.lng })
