package com.elsewhere.app.map

import com.elsewhere.app.analytics.Segment
import com.elsewhere.app.analytics.SegmentKind
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min

// Port of web/src/map/pace.ts

private const val GAP_RATE = 6 * 3600.0
/** Legs longer than this are "big": they keep screen time at any speed and the camera opens up before them. */
const val BIG_KM = 150.0

/** Real seconds a segment should take on screen at pace 1: legs 2.5–7 s by distance, stays 0.6–2.5 s by length. */
fun realSeconds(seg: Segment): Double = when (seg.kind) {
    SegmentKind.ACTIVITY -> min(7.0, max(2.5, 2 + log2(1 + seg.km) * 0.6))
    SegmentKind.VISIT -> min(2.5, max(0.6, 0.6 + ((seg.end - seg.start) / 3600.0) * 0.2))
    SegmentKind.GAP -> (seg.end - seg.start) / GAP_RATE
}

/** Screen time a segment gets at a speed: everything shrinks with speed except big legs, which keep 1.2–4.5 s by distance so a flight is never a flash. */
fun screenSeconds(seg: Segment, speed: Double): Double {
    val base = realSeconds(seg) / max(1e-6, speed)
    if (seg.kind != SegmentKind.ACTIVITY || seg.km < BIG_KM) return base
    return max(base, min(4.5, 1.2 + log2(seg.km / BIG_KM) * 0.75))
}
