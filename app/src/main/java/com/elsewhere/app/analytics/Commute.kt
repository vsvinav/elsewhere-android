package com.elsewhere.app.analytics

import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Record
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.floor
import kotlin.math.min

// Port of web/src/analytics/commute.ts. The web's `Leg` is `CommuteLeg` here: legs.ts already owns `Leg`.

enum class Direction(val key: String) { TO_WORK("to_work"), TO_HOME("to_home") }
data class CommuteLeg(
    val direction: Direction,
    val start: Long, val end: Long, val tz: Int,
    val minutes: Double,
    val modes: List<String>,
    val path: List<LatLng>,
)
data class LegStats(
    val n: Int, val medianMin: Double, val p90Min: Double,
    /** Mon..Sun counts */
    val byWeekday: List<Int>,
    /** 0..23 departure counts */
    val byHour: List<Int>,
    /** legs that used this mode */
    val byMode: Map<String, Int>,
)
data class CommuteStats(val legs: List<CommuteLeg>, val toWork: LegStats, val toHome: LegStats, val medianAll: Double)

private const val MAX_COMMUTE_SECONDS = 3 * 3600L

private fun quantile(xs: List<Double>, q: Double): Double {
    if (xs.isEmpty()) return 0.0
    val s = xs.sorted()
    return s[min(s.size - 1, floor(q * s.size).toInt())]
}

private fun legStats(legs: List<CommuteLeg>): LegStats {
    val mins = legs.map { it.minutes }
    val byWeekday = IntArray(7); val byHour = IntArray(24); val byMode = LinkedHashMap<String, Int>()
    for (l in legs) {
        val d = Instant.ofEpochSecond(l.start + l.tz * 60L).atOffset(ZoneOffset.UTC)
        byWeekday[d.dayOfWeek.value - 1]++
        byHour[d.hour]++
        for (m in LinkedHashSet(l.modes)) byMode[m] = (byMode[m] ?: 0) + 1
    }
    return LegStats(legs.size, quantile(mins, 0.5), quantile(mins, 0.9), byWeekday.toList(), byHour.toList(), byMode)
}

/** Home↔work legs: consecutive top-level visits home→work or work→home with the activities between them. */
fun commuteStats(data: Record, range: LongRange): CommuteStats {
    val visits = inRange(data.visits, range).filter { it.lvl == 0 }
    val acts = data.activities; val pl = data.places
    val legs = ArrayList<CommuteLeg>()
    var ai = 0
    for (i in 1 until visits.size) {
        val a = visits[i - 1]; val b = visits[i]
        val dir = when {
            isHome(pl[a.placeId]) && isWork(pl[b.placeId]) -> Direction.TO_WORK
            isWork(pl[a.placeId]) && isHome(pl[b.placeId]) -> Direction.TO_HOME
            else -> null
        } ?: continue
        val gap = b.start - a.end
        if (gap <= 0 || gap > MAX_COMMUTE_SECONDS) continue
        while (ai < acts.size && acts[ai].start < a.end) ai++
        val modes = ArrayList<String>()
        run { var j = ai; while (j < acts.size && acts[j].start < b.start) { modes.add(acts[j].mode); j++ } }
        // Prefer recorded GPS points between the two visits; fall back to activity endpoints.
        val path = arrayListOf(LatLng(a.lat, a.lng))
        for (pth in data.strokes) {
            if (pth.end < a.end || pth.start > b.start) continue
            for (p in pth.pts) if (p.ts >= a.end && p.ts <= b.start) path.add(LatLng(p.lat, p.lng))
        }
        if (path.size == 1) { var j = ai; while (j < acts.size && acts[j].start < b.start) { path.add(LatLng(acts[j].sLat, acts[j].sLng)); path.add(LatLng(acts[j].eLat, acts[j].eLng)); j++ } }
        path.add(LatLng(b.lat, b.lng))
        legs.add(CommuteLeg(dir, a.end, b.start, b.tz, gap / 60.0, modes, path))
    }
    val tw = legs.filter { it.direction == Direction.TO_WORK }; val th = legs.filter { it.direction == Direction.TO_HOME }
    return CommuteStats(legs, legStats(tw), legStats(th), quantile(legs.map { it.minutes }, 0.5))
}
