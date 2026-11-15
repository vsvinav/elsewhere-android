package com.elsewhere.app.map

import com.elsewhere.app.analytics.isHome
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Record

/** A place the replay has reached: the dots are the places you kept going back to; the Map tab pins every stop. */
class RevealDot(val id: String, val place: Place, val title: String)

/**
 * The reveal dots in order of first visit, so a frame finds how many have been reached by binary search and only
 * touches those; the reached set, which the labels want by visit count, is re-sorted only when it grows or shrinks.
 */
class RevealDots(data: Record) {
    private val byFirst: List<RevealDot> = data.places.entries
        .filter { (_, p) -> !isHome(p) && p.nVisits >= 2 }
        .map { (id, p) -> RevealDot(id, p, (p.name ?: p.label ?: "").trim()) }
        .sortedBy { it.place.firstTs }
    private var count = -1
    private var reached: List<RevealDot> = emptyList()

    val size: Int get() = byFirst.size

    /** The dots first visited by `now`, most visited first. */
    fun reachedBy(now: Long): List<RevealDot> {
        var lo = 0; var hi = byFirst.size
        while (lo < hi) { val m = (lo + hi) ushr 1; if (byFirst[m].place.firstTs <= now) lo = m + 1 else hi = m }
        if (lo != count) { count = lo; reached = byFirst.subList(0, lo).sortedByDescending { it.place.nVisits } }
        return reached
    }
}
