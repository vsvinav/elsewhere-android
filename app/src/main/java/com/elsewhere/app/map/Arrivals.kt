package com.elsewhere.app.map

import com.elsewhere.app.analytics.isHome
import com.elsewhere.app.analytics.placeName
import com.elsewhere.app.analytics.visitAt
import com.elsewhere.app.data.Record

// Port of the state behind web/src/map/ArrivalTags.tsx.

/** One luggage tag: the place arrived at, which visit this is, when it printed, and whether it is peeling off. */
data class Arrival(val key: Int, val placeId: String, val nth: Int, val atMs: Long, val leaving: Boolean = false)

const val ARRIVAL_TTL_MS = 7000L
const val ARRIVAL_LEAVE_MS = 420L
private const val KEEP = 3

/** Luggage tags that print when the playhead arrives somewhere new; a shelf of the last few, each falling off after a while. */
class Arrivals {
    var tags: List<Arrival> = emptyList(); private set
    private var lastVisit: Int? = null

    /** Note where the playhead is; a fresh top-level visit away from home prints a tag. */
    fun at(data: Record, playhead: Long, playing: Boolean, nowMs: Long) {
        if (!playing) return
        val v = visitAt(data, playhead) ?: return
        if (v.id == lastVisit) return
        lastVisit = v.id
        if (isHome(data.places[v.placeId])) return
        val nth = data.visits.count { it.lvl == 0 && it.placeId == v.placeId && it.start <= v.start }
        // A place already on show is reprinted in place; the oldest tag beyond KEEP peels off instead of vanishing.
        val name = placeName(data.places[v.placeId], v.placeId)
        val kept = tags.filter { it.placeId != v.placeId && placeName(data.places[it.placeId], it.placeId) != name && !it.leaving }
        val next = listOf(Arrival(v.id, v.placeId, nth, nowMs)) + kept
        tags = next.take(KEEP) + next.drop(KEEP).map { it.copy(leaving = true, atMs = nowMs - ARRIVAL_TTL_MS + ARRIVAL_LEAVE_MS) }
    }

    /** Age the shelf: tags start peeling in their last beat and are gone after the TTL. True when anything changed. */
    fun prune(nowMs: Long): Boolean {
        val out = tags.filter { nowMs - it.atMs < ARRIVAL_TTL_MS }.map { if (nowMs - it.atMs >= ARRIVAL_TTL_MS - ARRIVAL_LEAVE_MS) it.copy(leaving = true) else it }
        if (out == tags) return false
        tags = out
        return true
    }
}
