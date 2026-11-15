package com.elsewhere.app.ui.tabs.highlights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.elsewhere.app.data.Activity
import com.elsewhere.app.data.FeatureCollection
import com.elsewhere.app.data.Path
import com.elsewhere.app.data.Record
import com.elsewhere.app.analytics.Stop
import com.elsewhere.app.ui.world.TrailLayout
import com.elsewhere.app.ui.world.trailLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Twin of web/src/tabs/highlights/useReplay.ts and the playhead half of web/src/world/TripMap.tsx.

/** Roads driven and long flights in a range, with their times, so a playhead can cut them. */
data class RouteGeometry(val roads: List<Path>, val flights: List<Activity>)

/** Legs that touch the range. O(strokes + activities). */
fun routeGeometry(data: Record, range: LongRange) = RouteGeometry(
    data.strokes.filter { it.end >= range.first && it.start <= range.last && it.pts.size > 1 },
    data.activities.filter { it.end >= range.first && it.start <= range.last && it.mode == "FLYING" && it.m > 80_000 },
)

/** The part travelled by `playhead`: roads cut at the last point passed, a flight in progress drawn to where it is. O(points). */
fun routeAt(g: RouteGeometry, playhead: Long?): RouteGeometry {
    if (playhead == null) return g
    val roads = ArrayList<Path>()
    for (r in g.roads) {
        var n = 0
        while (n < r.pts.size && r.pts[n].ts <= playhead) n++
        if (n >= 2) roads.add(if (n == r.pts.size) r else r.copy(pts = r.pts.subList(0, n)))
    }
    val flights = ArrayList<Activity>()
    for (f in g.flights) {
        if (f.start > playhead) continue
        if (f.end <= playhead) { flights.add(f); continue }
        val k = (playhead - f.start).toDouble() / maxOf(1L, f.end - f.start)
        flights.add(f.copy(eLat = f.sLat + (f.eLat - f.sLat) * k, eLng = f.sLng + (f.eLng - f.sLng) * k))
    }
    return RouteGeometry(roads, flights)
}

/** The whole trip's fit, so the sheet does not jump while the route is being drawn. */
fun routeFit(data: Record, g: RouteGeometry, stops: List<Stop>, land: FeatureCollection?, height: Float) =
    trailLayout(data.copy(strokes = g.roads, activities = g.flights), stops, land, height)

/** The route as far as `seen`, printed in the fit of the whole trip. */
fun routeLayout(data: Record, seen: RouteGeometry, stops: List<Stop>, land: FeatureCollection?, fit: TrailLayout, height: Float) =
    trailLayout(data.copy(strokes = seen.roads, activities = seen.flights), stops, land, height, fit.proj)

/**
 * A scoped replay: the playhead sweeps `range` in `ms` of wall time, one step per frame, then parks at
 * the end. Local to the caller; the Replay tab's playhead is untouched.
 */
class ReplayState internal constructor(private val range: LongRange, private val ms: Long, private val launch: (suspend () -> Unit) -> Job) {
    var playhead by mutableStateOf<Long?>(null); private set
    var playing by mutableStateOf(false); private set
    private var job: Job? = null

    /** Park the playhead without playing; the parked state the snapshots print. */
    internal fun seek(at: Long) { job?.cancel(); playing = false; playhead = at }

    fun play() {
        job?.cancel()
        playhead = range.first; playing = true
        job = launch {
            var t0 = -1L
            while (true) {
                val t = withFrameNanos { it }
                if (t0 < 0) t0 = t
                val f = minOf(1.0, (t - t0) / 1e6 / ms)
                playhead = range.first + ((range.last - range.first) * f).toLong()
                if (f >= 1) break
            }
            playing = false
        }
    }
}

@Composable
fun rememberReplay(range: LongRange, ms: Long = 10_000): ReplayState {
    val scope = rememberCoroutineScope()
    return remember(range, ms) { ReplayState(range, ms) { block -> scope.launch { block() } } }
}
