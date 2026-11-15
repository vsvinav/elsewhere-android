package com.elsewhere.app.map

import com.elsewhere.app.analytics.Segment
import com.elsewhere.app.analytics.SegmentKind
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Located
import kotlin.math.max
import kotlin.math.min

// Port of web/src/map/frame.ts

data class Frame(val center: LatLng, val zoom: Double)

private const val STAY_ZOOM = 14.0
private const val SHORT_STAY_S = 1.6
private const val LEAD_S = 1.4

internal fun lerp(a: Located, b: Located, t: Double) = LatLng(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
private fun mid(s: Segment) = lerp(s.s, s.e, 0.5)
private fun legZoom(s: Segment, w: Int, h: Int) = fitZoom(s.s, s.e, w * 0.72, h * 0.72)

/**
 * Where the replay camera should be: a frame that holds the whole leg while the dot crosses it,
 * drifting a third of the way with the dot so it never pins to an edge. Short stops between legs
 * keep the wide frame instead of diving into town; the last beat of a stay already opens up for
 * the next leg, so a take-off never surprises.
 */
fun frameFor(seg: Segment, next: Segment?, here: Located, now: Long, rate: Double, w: Int, h: Int): Frame {
    if (seg.kind == SegmentKind.VISIT) {
        val total = realSeconds(seg); val left = max(0.0, (seg.end - now) / max(1.0, rate))
        val leg = if (next != null && next.kind != SegmentKind.VISIT && next.km > 2) next else null
        if (leg != null && (total < SHORT_STAY_S || left < LEAD_S)) {
            // Open the next leg's frame, easing its centre in from where we stand.
            val k = if (total < SHORT_STAY_S) 0.5 else 1 - left / LEAD_S
            return Frame(lerp(here, mid(leg), 0.35 * k), max(3.0, legZoom(leg, w, h) + (1 - k) * 2))
        }
        return Frame(LatLng(here.lat, here.lng), STAY_ZOOM)
    }
    val span = max(1L, seg.end - seg.start); val prog = min(1.0, max(0.0, (now - seg.start).toDouble() / span))
    val z = max(3.0, min(STAY_ZOOM + 1.5, legZoom(seg, w, h)))
    // Frame the leg; let the dot pull the centre a third of the way so it reads as travel, and land on the destination.
    val pull = if (prog > 0.85) 0.35 + ((prog - 0.85) / 0.15) * 0.65 else 0.35
    return Frame(lerp(mid(seg), here, pull), z)
}
