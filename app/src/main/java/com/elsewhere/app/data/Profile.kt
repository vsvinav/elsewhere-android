package com.elsewhere.app.data

import kotlinx.serialization.Serializable

@Serializable data class Home(val lat: Double, val lng: Double, val city: String, val cc: String, val from: Long, val to: Long?, val name: String? = null)
@Serializable data class Work(val lat: Double, val lng: Double, val placeId: String, val name: String? = null, val from: Long, val to: Long?)
@Serializable data class Profile(val name: String?, val units: Units, val homes: List<Home>, val works: List<Work>)

/** Works whose window covers `ts`; `to` is exclusive, null never ends. */
fun worksAt(works: List<Work>, ts: Long): List<Work> = works.filter { w -> w.from <= ts && (w.to == null || ts < w.to) }

/** The home active at `ts`; homes sorted by `from`. Before the first window → first home. Past a
 *  home's `to` the next home takes over early (you were on your way); the last home never ends. */
fun homeAt(homes: List<Home>, ts: Long): Home {
    var cur = homes[0]
    homes.forEachIndexed { i, h ->
        if (h.from <= ts) {
            cur = h
            if (h.to != null && ts >= h.to && i + 1 < homes.size) cur = homes[i + 1]
        }
    }
    return cur
}
