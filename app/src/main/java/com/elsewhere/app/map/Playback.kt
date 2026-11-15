package com.elsewhere.app.map

import com.elsewhere.app.analytics.Lens
import com.elsewhere.app.analytics.Segment
import com.elsewhere.app.analytics.SegmentKind
import com.elsewhere.app.analytics.inLens
import com.elsewhere.app.analytics.segmentAt
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Record
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest

// Port of web/src/map/usePlayback.ts: the clock, without the store.

/**
 * The replay's clock. `playhead` is epoch seconds, fractional so a slow pace still advances every frame;
 * `rate` is record seconds per real second as the visuals see it, eased so trail length never snaps.
 */
data class PlayState(
    val playhead: Double, val playing: Boolean, val speed: Double = 1.0, val rate: Double = 3600.0,
    val lens: Lens = Lens.ALL, val follow: Boolean = true,
)

/** Advances the playhead by `dt` real seconds, pacing by segment so every movement is readable and no stay drags. */
fun tickPlayback(data: Record, s: PlayState, dt: Double): PlayState {
    if (!s.playing) return s
    val step = min(0.1, dt)
    val r0 = data.meta.range.first; val r1 = data.meta.range.last
    var cur = s.playhead
    val seg: Segment
    if (s.lens != Lens.ALL) {
        // Under a lens there are only legs: play the one we are in, else cut to the next.
        val legs = data.activities
        var leg = legs.firstOrNull { inLens(it.mode, s.lens) && it.start <= cur && cur <= it.end }
        if (leg == null) {
            leg = legs.firstOrNull { inLens(it.mode, s.lens) && it.start > cur } ?: return s.copy(playing = false)
            cur = leg.start.toDouble()
        }
        seg = Segment(SegmentKind.ACTIVITY, leg.start, leg.end, LatLng(leg.sLat, leg.sLng), LatLng(leg.eLat, leg.eLng), leg.mode, leg.m / 1000)
    } else seg = segmentAt(data, cur.toLong())
    val rate = min(48 * 3600 * max(1.0, s.speed), max(60.0, (seg.end - seg.start) / screenSeconds(seg, s.speed)))
    val next = min(cur + step * rate, seg.end + 1.0)
    if (next >= r1) return s.copy(playhead = r1.toDouble(), playing = false)
    // The rate the visuals see eases, so trail length does not snap at every segment boundary.
    val shown = s.rate + (rate - s.rate) * min(1.0, step / 0.8)
    return s.copy(playhead = next, rate = shown)
}

/**
 * Which states the chrome under the map needs to see: the canvas reads the clock every frame on its own, so the HUD
 * and scrub only need the playhead a few times a second, while a pause, speed, lens or follow change shows at once.
 */
class ChromeGate(private val periodMs: Long = 250) {
    private var shown: PlayState? = null
    private var at = 0L

    /** How long to hold this state back: zero to show now, else the rest of the beat since the last shown clock. */
    fun holdFor(s: PlayState, nowMs: Long): Long {
        val prev = shown
        val clockOnly = prev != null && prev.copy(playhead = s.playhead, rate = s.rate) == s
        return if (clockOnly) max(0L, at + periodMs - nowMs) else 0L
    }

    fun shown(s: PlayState, nowMs: Long) { shown = s; at = nowMs }

    fun pass(s: PlayState, nowMs: Long): Boolean {
        if (holdFor(s, nowMs) > 0) return false
        shown(s, nowMs)
        return true
    }
}

/** The states the chrome sees, held to the gate's beat; a held clock still lands once the beat is up, so a scrub never sticks. */
fun Flow<PlayState>.chromeGated(gate: ChromeGate, nowMs: () -> Long): Flow<PlayState> = transformLatest { s ->
    val wait = gate.holdFor(s, nowMs())
    if (wait > 0) delay(wait)
    gate.shown(s, nowMs())
    emit(s)
}
