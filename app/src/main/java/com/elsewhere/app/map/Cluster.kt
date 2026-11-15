package com.elsewhere.app.map

import com.elsewhere.app.data.Located
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

// Port of web/src/map/cluster.ts

/** Points that fell into one screen cell, placed at their mean. */
data class Group<T : Located>(val lng: Double, val lat: Double, val items: List<T>)

data class Clustered<T : Located>(val singles: List<T>, val groups: List<Group<T>>)

private fun mercY(lat: Double): Double = ln(tan(PI / 4 + (lat.coerceIn(-85.0, 85.0) * PI) / 360)) * (180 / PI)

/** Grid clustering in screen space: anything sharing a `cell` px square at this zoom becomes one group. */
fun <T : Located> cluster(pts: List<T>, zoom: Double, cell: Int = 44): Clustered<T> {
    val scale = (256 * 2.0.pow(zoom)) / 360
    val cells = LinkedHashMap<Long, MutableList<T>>()
    for (p in pts) {
        val cx = floor(((p.lng + 180) * scale) / cell).toLong()
        val cy = floor(((180 - mercY(p.lat)) * scale) / cell).toLong()
        cells.getOrPut((cx shl 32) xor (cy and 0xffffffffL)) { ArrayList() }.add(p)
    }
    val singles = ArrayList<T>(); val groups = ArrayList<Group<T>>()
    for (items in cells.values) {
        if (items.size == 1) { singles.add(items[0]); continue }
        groups.add(Group(items.sumOf { it.lng } / items.size, items.sumOf { it.lat } / items.size, items))
    }
    return Clustered(singles, groups)
}
