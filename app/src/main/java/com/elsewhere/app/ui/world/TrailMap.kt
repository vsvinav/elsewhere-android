package com.elsewhere.app.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.analytics.Stop
import com.elsewhere.app.data.Activity
import com.elsewhere.app.data.FeatureCollection
import com.elsewhere.app.data.Record
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

// Port of web/src/world/TrailMap.tsx. The geometry is laid out once, off the main thread, in a
// 1100 × 300 sheet; the composable only scales and inks it.

const val TRAIL_W = 1100f
const val TRAIL_H = 300f
/** Fixes closer than this on the sheet, in sheet units, add nothing to a stroke and are dropped. */
const val TRAIL_GAP = 1.5f
private const val PAD = 36f

data class TrailPin(val city: String, val x: Float, val y: Float, val first: Boolean, val alignEnd: Boolean)

/** The equirectangular fit of one layout: longitude scaled by `k`, then both axes by `s` into the sheet. */
data class TrailProj(val k: Double, val x0: Double, val y1: Double, val s: Double, val ox: Double, val oy: Double) {
    fun px(lng: Double) = ((lng * k - x0) * s + ox).toFloat()
    fun py(lat: Double) = ((y1 - lat) * s + oy).toFloat()
}

/** Everything in sheet units: polylines as flat [x, y, x, y…], flights as [ax, ay, cx, cy, bx, by]. */
data class TrailLayout(
    val lines: List<FloatArray> = emptyList(), val flights: List<FloatArray> = emptyList(),
    val pins: List<TrailPin> = emptyList(), val land: List<FloatArray> = emptyList(), val proj: TrailProj? = null,
)

private fun quantile(sorted: DoubleArray, p: Double): Double = sorted[floor(p * (sorted.size - 1)).toInt()]

/**
 * Fit the whole record with a little air (1st to 99th percentile, so one stray fix can't shrink the
 * rest); flights pull the frame wide, which is the point of the poster. Equirectangular, scaled by
 * the cosine of the median latitude. Strokes are thinned afterwards, by distance on the sheet, so
 * every one still starts and ends where it did however long the record.
 */
fun trailLayout(data: Record, stops: List<Stop>, land: FeatureCollection?, height: Float = TRAIL_H): TrailLayout {
    val raw = data.strokes.map { it.pts }.filter { it.size > 1 }
    // One arc per pair of airports: a return leg over the same arc would fill in the other's dashes.
    val fl = oneArcPerRoute(data.activities.filter { it.mode == "FLYING" && it.m > 80_000 })
    val n = raw.sumOf { it.size } + fl.size * 2
    if (n == 0) return TrailLayout()
    val lngs = DoubleArray(n); val lats = DoubleArray(n)
    var i = 0
    for (l in raw) for (p in l) { lngs[i] = p.lng; lats[i] = p.lat; i++ }
    for (a in fl) { lngs[i] = a.sLng; lats[i] = a.sLat; i++; lngs[i] = a.eLng; lats[i] = a.eLat; i++ }
    val sortedLats = lats.sortedArray()
    val k = cos(quantile(sortedLats, 0.5) * PI / 180)
    val xs = DoubleArray(n) { lngs[it] * k }.sortedArray()
    // The cities slept in are always framed, however little of the record was logged there.
    val x0 = min(quantile(xs, 0.01), stops.minOfOrNull { it.lng * k } ?: Double.MAX_VALUE)
    val x1 = max(quantile(xs, 0.99), stops.maxOfOrNull { it.lng * k } ?: -Double.MAX_VALUE)
    val y0 = min(quantile(sortedLats, 0.01), stops.minOfOrNull { it.lat } ?: Double.MAX_VALUE)
    val y1 = max(quantile(sortedLats, 0.99), stops.maxOfOrNull { it.lat } ?: -Double.MAX_VALUE)
    val s = min((TRAIL_W - 2 * PAD) / max(1e-6, x1 - x0), (height - 2 * PAD) / max(1e-6, y1 - y0))
    val ox = (TRAIL_W - (x1 - x0) * s) / 2; val oy = (height - (y1 - y0) * s) / 2
    val proj = TrailProj(k, x0, y1, s, ox, oy)
    fun px(lng: Double) = proj.px(lng)
    fun py(lat: Double) = proj.py(lat)

    val lines = raw.map { l ->
        val out = ArrayList<Float>(l.size * 2)
        var lx = Float.NaN; var ly = Float.NaN
        for ((j, p) in l.withIndex()) {
            val x = px(p.lng); val y = py(p.lat)
            if (j == 0 || j == l.size - 1 || hypot(x - lx, y - ly) >= TRAIL_GAP) { out.add(x); out.add(y); lx = x; ly = y }
        }
        out.toFloatArray()
    }
    val flights = fl.map { a ->
        val ax = px(a.sLng); val ay = py(a.sLat); val bx = px(a.eLng); val by = py(a.eLat)
        val dx = bx - ax; val dy = by - ay; val len = max(1e-6f, hypot(dx, dy))
        // Control point lifts the arc toward the top of the sheet, like a route drawn by hand.
        val cx = (ax + bx) / 2 - (dy / len) * len * 0.22f * (sign(dx).takeIf { it != 0f } ?: 1f)
        val cy = (ay + by) / 2 - abs(dx / len) * len * 0.22f
        floatArrayOf(ax, ay, cx, cy, bx, by)
    }
    // Cities that would print on top of one another keep only the one with more nights. The whole list is
    // walked, not just the busiest few, so a year of suburbs does not hide the far ends of the record.
    val placed = ArrayList<TrailPin>()
    for ((idx, st) in stops.withIndex()) {
        if (placed.size >= MAX_PINS) break
        val x = px(st.lng); val y = py(st.lat)
        val clash = placed.any { q -> abs(q.x - x) < (q.city.length + st.city.length) * 7 + 24 && abs(q.y - y) < 24 }
        if (!clash) placed.add(TrailPin(st.city, x, y, idx == 0, x > TRAIL_W - 160))
    }
    val landRings = land?.rings?.flatMap(::splitAntimeridian)?.map { r -> FloatArray(r.size).also { out -> var j = 0; while (j + 1 < r.size) { out[j] = px(r[j]); out[j + 1] = py(r[j + 1]); j += 2 } } } ?: emptyList()
    return TrailLayout(lines, flights, placed, landRings, proj)
}

private const val MAX_PINS = 7

/**
 * Where the red dots of the roads go, as flat [x, y…] in pixels: one every `pitch` pixels along each
 * polyline (sheet units scaled by `u`), carried across the corners, and one on the far end so no road
 * stops short of the town it reached. The web gets this from an SVG dash of "0 7" with round caps.
 */
fun roadDots(lines: List<FloatArray>, u: Float, pitch: Float): FloatArray {
    val out = FloatBuf()
    for (l in lines) {
        if (l.size < 4) continue
        var carry = 0f
        var j = 2
        var lastX = l[0] * u; var lastY = l[1] * u
        out.add(lastX, lastY)
        while (j + 1 < l.size) {
            val ax = l[j - 2] * u; val ay = l[j - 1] * u; val bx = l[j] * u; val by = l[j + 1] * u
            val len = hypot(bx - ax, by - ay)
            var d = pitch - carry
            while (d <= len) {
                lastX = ax + (bx - ax) * d / len; lastY = ay + (by - ay) * d / len
                out.add(lastX, lastY)
                d += pitch
            }
            carry = len - (d - pitch)
            j += 2
        }
        val ex = l[l.size - 2] * u; val ey = l[l.size - 1] * u
        if (hypot(ex - lastX, ey - lastY) >= pitch / 2) out.add(ex, ey)
    }
    return out.toArray()
}

/** A growing primitive float buffer: thousands of dots a frame are not worth a boxed Float each. */
private class FloatBuf {
    private var a = FloatArray(1024); private var n = 0
    fun add(x: Float, y: Float) {
        if (n + 2 > a.size) a = a.copyOf(a.size * 2)
        a[n++] = x; a[n++] = y
    }
    fun toArray(): FloatArray = a.copyOf(n)
}

/** Within 0.3° of each other, as the Flights page tells a hub's runways apart; nearness, not a grid, so no cell edge splits an airport. */
private fun sameAirport(aLng: Double, aLat: Double, bLng: Double, bLat: Double) =
    hypot((aLng - bLng) * cos(aLat * PI / 180), aLat - bLat) < 0.3

/** The first leg flown over each pair of airports, either way round. */
internal fun oneArcPerRoute(flights: List<Activity>): List<Activity> {
    val kept = ArrayList<Activity>()
    for (a in flights) {
        val dup = kept.any { k ->
            (sameAirport(a.sLng, a.sLat, k.sLng, k.sLat) && sameAirport(a.eLng, a.eLat, k.eLng, k.eLat)) ||
                (sameAirport(a.sLng, a.sLat, k.eLng, k.eLat) && sameAirport(a.eLng, a.eLat, k.sLng, k.sLat))
        }
        if (!dup) kept.add(a)
    }
    return kept
}

/**
 * A ring that steps across the antimeridian (Russia, Fiji in the atlas) would print as a line the
 * width of the sheet on a flat map, so it is cut into one closed ring per side; each piece closes
 * along the meridian, which is where the coast is anyway.
 */
fun splitAntimeridian(ring: DoubleArray): List<DoubleArray> {
    var crosses = false
    var j = 2
    while (j + 1 < ring.size) { if (abs(ring[j] - ring[j - 2]) > 180) { crosses = true; break }; j += 2 }
    if (!crosses) return listOf(ring)
    val runs = ArrayList<ArrayList<Double>>()
    var side = 0.0
    j = 0
    while (j + 1 < ring.size) {
        val s = sign(ring[j]).takeIf { it != 0.0 } ?: side
        if (runs.isEmpty() || (s != side && abs(ring[j] - ring[j - 2]) > 180)) { runs.add(ArrayList()); side = s }
        runs.last().add(ring[j]); runs.last().add(ring[j + 1])
        j += 2
    }
    // The ring is a cycle, so a first and last run on the same side are one piece.
    if (runs.size > 1 && sign(runs.first()[0]) == sign(runs.last()[0])) { runs.last().addAll(runs.removeAt(0)) }
    return runs.filter { it.size >= 6 }.map { it.toDoubleArray() }
}
private val HALO = listOf(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f), floatArrayOf(0f, 1f), floatArrayOf(0f, -1f), floatArrayOf(0.7f, 0.7f), floatArrayOf(-0.7f, 0.7f), floatArrayOf(0.7f, -0.7f), floatArrayOf(-0.7f, -0.7f))

/** A pin in canvas pixels with the size of its measured name. */
data class LabelAnchor(val x: Float, val y: Float, val w: Float, val h: Float, val alignEnd: Boolean)

/**
 * Where each name prints, or null when there is no room. A name sits beside its pin on the side the
 * layout asked for, slides to the other side if that would leave the sheet or cover a name already
 * placed, and is dropped if neither side is clear. Boxes are nudged back inside the sheet vertically.
 */
fun labelBoxes(anchors: List<LabelAnchor>, width: Float, height: Float, gap: Float): List<Rect?> {
    val kept = ArrayList<Rect>()
    return anchors.map { a ->
        val top = (a.y - a.h / 2).coerceIn(0f, max(0f, height - a.h))
        val right = Rect(Offset(a.x + gap, top), Size(a.w, a.h))
        val left = Rect(Offset(a.x - gap - a.w, top), Size(a.w, a.h))
        val sheet = Rect(0f, 0f, width, height)
        val order = if (a.alignEnd) listOf(left, right) else listOf(right, left)
        order.firstOrNull { b -> b.left >= sheet.left && b.right <= sheet.right && kept.none { it.overlaps(b) } }?.also { kept.add(it) }
    }
}

private val SheetFill = Color(0xFFE2D6B8)
private val Coast = Color(0xFFB9AE92)
// The web's SVG strokes, in CSS pixels. They are drawn in device pixels here, never scaled with the
// sheet, so the trail stays a run of separate dots on a phone instead of collapsing into a thin line.
private const val COAST_W = 0.8f
private const val ROAD_W = 3.5f
private const val ROAD_GAP = 7f
private const val FLIGHT_W = 1.6f
private val FLIGHT_DASH = floatArrayOf(5f, 6f)
private const val PIN_R = 5f
private const val PIN_R_FIRST = 7f
private const val PIN_RIM = 2.5f

/**
 * The record printed as a poster map: cream continents with an ink coast, roads as runs of red
 * dots, flights as black arcs lifted off the sheet, and the cities you slept in named.
 */
@Composable
fun TrailMap(layout: TrailLayout, modifier: Modifier = Modifier, height: Float = TRAIL_H) {
    val measurer = rememberTextMeasurer()
    val paths = remember(layout) {
        val land = Path().apply {
            fillType = PathFillType.EvenOdd
            for (r in layout.land) { var j = 0; while (j + 1 < r.size) { if (j == 0) moveTo(r[0], r[1]) else lineTo(r[j], r[j + 1]); j += 2 }; close() }
        }
        val flights = Path().apply {
            for (f in layout.flights) { moveTo(f[0], f[1]); quadraticTo(f[2], f[3], f[4], f[5]) }
        }
        land to flights
    }
    // The web sets names in sheet units; on a phone the sheet is a third the size, so names keep a readable sp size instead.
    val labelStyle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 0.04.em, lineHeight = 1.em)
    val texts = remember(layout, measurer, labelStyle) { layout.pins.map { p -> measurer.measure(p.city.uppercase(), labelStyle) } }
    // The dots depend only on the layout and the width it is drawn at, so they are placed once per width, not per frame.
    val dotCache = remember(layout) { HashMap<Float, FloatArray>() }
    // The sheet is clipped: the land and the arcs run well past the frame, as the SVG's viewBox cuts them on the web.
    // Its own layer keeps the display list between scroll frames.
    Canvas(
        modifier.fillMaxWidth().aspectRatio(TRAIL_W / height).graphicsLayer().clipToBounds().background(SheetFill).border(2.dp, Poster.Ink)
            .semantics { contentDescription = "Map of the record" },
    ) {
        val u = size.width / TRAIL_W
        // The geometry is scaled into place; every width and dash is divided back out so it lands in device pixels.
        scale(u, u, pivot = Offset.Zero) {
            drawPath(paths.first, Poster.Stock, style = Fill)
            drawPath(paths.first, Coast, style = Stroke(max(COAST_W, 1f) / u))
            drawPath(paths.second, Poster.Ink, style = Stroke(FLIGHT_W / u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(FLIGHT_DASH[0] / u, FLIGHT_DASH[1] / u))))
        }
        // The roads are runs of separate dots, placed in pixels; a dashed stroke this fine does not survive every renderer.
        val dots = dotCache.getOrPut(size.width) { roadDots(layout.lines, u, ROAD_GAP) }
        var k = 0
        while (k + 1 < dots.size) { drawCircle(Poster.Red, ROAD_W / 2, Offset(dots[k], dots[k + 1])); k += 2 }
        for (p in layout.pins) {
            val c = Offset(p.x * u, p.y * u)
            val r = if (p.first) PIN_R_FIRST else PIN_R
            drawCircle(Poster.Red, r, c)
            drawCircle(Poster.Stock, r, c, style = Stroke(PIN_RIM))
        }
        // Names are set in sp, not sheet units, so where they fit is settled here in pixels. The web's
        // `paint-order: stroke` halo is the same name printed in stock around the ink, eight ways.
        val anchors = layout.pins.mapIndexed { i, p -> LabelAnchor(p.x * u, p.y * u, texts[i].size.width.toFloat(), texts[i].size.height.toFloat(), p.alignEnd) }
        val boxes = labelBoxes(anchors, size.width, size.height, gap = 9f * u)
        val halo = 1.5.dp.toPx()
        boxes.forEachIndexed { i, box ->
            if (box == null) return@forEachIndexed
            for (d in HALO) drawText(texts[i], Poster.Stock, box.topLeft + Offset(d[0] * halo, d[1] * halo))
            drawText(texts[i], Poster.Ink, box.topLeft)
        }
    }
}
