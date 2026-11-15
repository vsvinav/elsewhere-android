package com.elsewhere.app.map

import com.elsewhere.app.analytics.Segment
import com.elsewhere.app.analytics.SegmentKind
import com.elsewhere.app.analytics.bearing
import kotlin.math.min

// Port of web/src/map/heading.ts: where the replay stamp points.

/** Legs shorter than this keep the glyph upright, so a short hop is still read as a car or a walker rather than a tilt. */
const val POINT_KM = 15.0
/** How long the glyph takes to swing onto a new heading. */
const val HEADING_EASE_MS = 350L
/** A frame this long after the last one snaps instead of sweeping from where it was. */
private const val STALE_MS = 500L

/**
 * Where the stamp points: one heading per leg, the great-circle bearing from its start to its end, so the glyph knows
 * a general direction without following every wobble of the trace. `null` means upright.
 */
fun stampHeading(seg: Segment?, kind: Stamp): Double? {
    if (seg == null || kind == Stamp.STILL || kind == Stamp.FF || seg.kind != SegmentKind.ACTIVITY || seg.km < POINT_KM) return null
    if (seg.s.lng == seg.e.lng && seg.s.lat == seg.e.lat) return null
    return bearing(seg.s, seg.e)
}

/** The shown heading, eased toward its target along the shortest arc; a lost heading eases back upright. */
class HeadingEase {
    private var shown = 0.0; private var last = 0L; private var live = false
    fun at(want: Double?, tMs: Long): Double {
        val target = want ?: 0.0
        if (!live || tMs - last > STALE_MS) { shown = target; live = true }
        else {
            val k = min(1.0, (tMs - last).toDouble() / HEADING_EASE_MS)
            if (k >= 1.0) shown = target
            else {
                val d = ((target - shown + 540) % 360) - 180
                shown = ((shown + d * k) % 360 + 360) % 360
            }
        }
        last = tMs
        return shown
    }
}
