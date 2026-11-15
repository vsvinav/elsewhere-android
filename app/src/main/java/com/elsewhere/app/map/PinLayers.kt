package com.elsewhere.app.map

import com.elsewhere.app.analytics.Cat
import com.elsewhere.app.analytics.categoryOf
import com.elsewhere.app.analytics.isHome
import com.elsewhere.app.data.Located
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Record
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

// Port of the pin half of web/src/map/layers.ts (`pinLayers`). deck.gl layers become plain data the screen draws.

enum class SizeBy(val label: String) { VISITS("visits"), HOURS("hours") }

data class PinOpts(val hidden: Set<Cat> = emptySet(), val selected: String? = null, val sizeBy: SizeBy = SizeBy.VISITS, val query: String = "")

/** A stop ready to pin: its place, the name on the label, its kind and that kind's ink as `#rrggbb`. */
data class Dot(val id: String, val place: Place, val title: String, val cat: Cat, val ink: String) : Located {
    override val lat get() = place.lat
    override val lng get() = place.lng
}

/** One drawn marker; `size` is the icon's height in px. Pins anchor at their tip, houses at their centre. */
data class Pin(val dot: Dot, val size: Float, val home: Boolean)

/** An ink disc for stops sharing a cell, its count printed in stock; its radius follows `k`, see [clusterRadius]. */
data class ClusterPin(override val lat: Double, override val lng: Double, val items: List<Dot>) : Located { val count get() = items.size }

/** A caps label beside a pin, offset (`dx`, `dy`) px from the point. */
data class PinLabel(val dot: Dot, val text: String, val dx: Float, val dy: Float)

/** The open pin redrawn on top: a stock halo, then a larger ink back and coloured face. */
data class SelectedPin(val pin: Pin, val backSize: Float, val topSize: Float, val haloRadius: Float = HALO_PX)

data class PinScene(
    val k: Float, val clusters: List<ClusterPin>, val pins: List<Pin>, val homes: List<Pin>,
    val labels: List<PinLabel>, val countSize: Float, val selected: SelectedPin?,
)

data class PinSummary(val stops: Int, val cities: Int, val countries: Int)

const val HALO_PX = 18f
const val LABEL_PX = 11f
const val HOME_INK = "#d63c20"
private const val CELL_PX = 60

/** Zoomed out, pins shrink with the map so a cluster never blankets an island. */
fun zoomFactor(zoom: Double): Float =
    (((zoom - 1) / 5).coerceIn(0.45, 1.0) + ((zoom - 11) / 10).coerceIn(0.0, 0.3)).toFloat()

fun pinSize(k: Float, d: Dot, sizeBy: SizeBy): Float {
    val weight = if (sizeBy == SizeBy.VISITS) d.place.nVisits.toDouble() else d.place.totalHours / 3
    return k * (16 + min(14.0, sqrt(weight) * 2.2)).toFloat()
}

/** Radius of a cluster disc at this `k`, sized by how many stops it holds. */
fun clusterRadius(c: ClusterPin, k: Float): Float = k * (10 + min(12.0, log2(c.count.toDouble()) * 2.2)).toFloat()

fun summary(dots: List<Dot>): PinSummary =
    PinSummary(dots.size, dots.mapNotNullTo(HashSet()) { it.place.city }.size, dots.mapNotNullTo(HashSet()) { it.place.countryCode }.size)

/** Greedy screen-space culling: most-visited label wins its box; nothing below zoom 6. */
private fun labelDots(dots: List<Dot>, zoom: Double): List<Dot> {
    if (zoom < 6) return emptyList()
    val scale = (256 * 2.0.pow(zoom)) / 360
    val kept = ArrayList<DoubleArray>(); val out = ArrayList<Dot>()
    for (d in dots) {
        if (d.title.isEmpty()) continue
        val y = ln(tan(PI / 4 + (d.lat * PI) / 360)) * (180 / PI)
        val x = (d.lng + 180) * scale + 12; val py = (180 - y) * scale - 8
        val w = d.title.length * 7.2 + 24; val h = 22.0
        if (kept.any { (kx, ky, kw, kh) -> x < kx + kw && x + w > kx && py < ky + kh && py + h > ky }) continue
        kept.add(doubleArrayOf(x, py, w, h)); out.add(d)
    }
    return out
}

private operator fun DoubleArray.component4() = this[3]

/**
 * Every stop in the record as pins and nothing else: the Map tab. One instance per record; the shown set is
 * cached per filter and its clusters and labels per half-zoom, so a pan costs a map lookup and a zoom one regrid.
 */
class PinLayers(record: Record) {
    /** The window these pins were cut to; a door checks it before framing them. */
    val range: LongRange = record.meta.range
    /** Non-home stops, most visited first. */
    val all: List<Dot> = record.places.entries.filter { !isHome(it.value) }
        .map { (id, p) -> val cat = categoryOf(p); Dot(id, p, (p.name ?: p.label ?: "").trim(), cat, cat.color) }
        .sortedByDescending { it.place.nVisits }
    /** Homes stay put whatever is toggled: a red house with an ink outline. */
    val homes: List<Dot> = record.places.entries.filter { isHome(it.value) }
        .map { (id, p) -> Dot(id, p, (p.name ?: p.label ?: "Home").trim(), Cat.OTHER, HOME_INK) }
    val counts: Map<Cat, Int> = all.groupingBy { it.cat }.eachCount()

    private val byId: Map<String, Dot> by lazy { (all + homes).associateBy { it.id } }

    private class Shown(val dots: List<Dot>) {
        val clusters = HashMap<Double, List<ClusterPin>>()
        val singles = HashMap<Double, List<Dot>>()
        val labels = HashMap<Double, List<Dot>>()
    }
    private var shownKey: Pair<Set<Cat>, String>? = null
    private var shown: Shown = Shown(all)

    private fun shownFor(o: PinOpts): Shown {
        val q = o.query.trim().lowercase()
        val key = o.hidden to q
        if (shownKey != key) {
            shownKey = key
            shown = Shown(all.filter { d -> d.cat !in o.hidden && (q.isEmpty() || d.title.lowercase().contains(q)) })
        }
        return shown
    }

    /** The stops the current filter and search leave on the map. */
    fun shown(o: PinOpts): List<Dot> = shownFor(o).dots

    fun scene(zoom: Double, o: PinOpts): PinScene {
        val s = shownFor(o)
        val zb = kotlin.math.floor(zoom * 2) / 2
        val k = zoomFactor(zoom)
        if (zb !in s.clusters) {
            val cl = cluster(s.dots, zb, CELL_PX)
            s.singles[zb] = cl.singles
            s.clusters[zb] = cl.groups.map { g -> ClusterPin(g.lat, g.lng, g.items) }
        }
        val singles = s.singles.getValue(zb)
        // Homes label first so they always win their box.
        val labelled = s.labels.getOrPut(zb) { labelDots(homes + singles, zb) }
        val clusters = s.clusters.getValue(zb)
        val pins = singles.map { Pin(it, pinSize(k, it, o.sizeBy), home = false) }
        val homePins = homes.map { Pin(it, 20 * k, home = true) }
        val sel = o.selected?.let { id -> all.firstOrNull { it.id == id } ?: homes.firstOrNull { it.id == id } }
        val selected = sel?.let {
            val home = it in homes
            val size = if (home) 20 * k else pinSize(k, it, o.sizeBy)
            SelectedPin(Pin(it, size, home), backSize = if (home) 34 * k else size + 12, topSize = if (home) 27 * k else size + 8)
        }
        return PinScene(
            k = k, clusters = clusters, pins = pins, homes = homePins,
            labels = labelled.map { PinLabel(it, it.title.uppercase(), 9 * k, -13 * k) },
            countSize = maxOf(9f, 12 * k), selected = selected,
        )
    }

    /** The box around every shown stop and every home; null when there is nothing to frame. */
    fun fitBounds(o: PinOpts): Bounds? = boundsAround(shownFor(o).dots + homes)

    /** The box around the stops named, ignoring unknown ids; null when none is on the map. O(ids) after the first call. */
    fun boundsOf(ids: Collection<String>): Bounds? = boundsAround(ids.mapNotNull { byId[it] })
}

/** A lone stop gets a box about a kilometre across, so framing it does not land at street level. */
private const val LONE_PAD = 0.005

private fun boundsAround(pts: List<Located>): Bounds? {
    if (pts.isEmpty()) return null
    val b = Bounds(pts.minOf { it.lat }, pts.minOf { it.lng }, pts.maxOf { it.lat }, pts.maxOf { it.lng })
    return if (b.swLat == b.neLat && b.swLng == b.neLng) Bounds(b.swLat - LONE_PAD, b.swLng - LONE_PAD, b.neLat + LONE_PAD, b.neLng + LONE_PAD) else b
}
