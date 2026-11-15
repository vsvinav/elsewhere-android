package com.elsewhere.app.map

import com.elsewhere.app.analytics.Segment
import com.elsewhere.app.analytics.SegmentKind
import com.elsewhere.app.analytics.segmentAt
import com.elsewhere.app.data.Record
import kotlin.math.max
import kotlin.math.min

// Port of web/src/map/lookahead.ts

private const val MAX_STEPS = 400

/** A big leg ahead and the real seconds of screen time until it starts. */
data class Upcoming(val seg: Segment, val inS: Double)

/**
 * The first big leg that starts within `leadS` real seconds of screen time at this speed, walking segment by
 * segment. Measured in screen time, not record time, so it is steady across slow and fast segments.
 */
fun upcomingBig(data: Record, now: Long, speed: Double, leadS: Double): Upcoming? {
    var seg = segmentAt(data, now)
    var inS = screenSeconds(seg, speed) * max(0.0, min(1.0, (seg.end - now).toDouble() / max(1L, seg.end - seg.start)))
    var i = 0
    while (i < MAX_STEPS && inS <= leadS) {
        val nxt = segmentAt(data, seg.end + 2)
        if (nxt.start < seg.end) return null
        if (nxt.kind == SegmentKind.ACTIVITY && nxt.km >= BIG_KM) return Upcoming(nxt, inS)
        inS += screenSeconds(nxt, speed)
        seg = nxt
        i++
    }
    return null
}
