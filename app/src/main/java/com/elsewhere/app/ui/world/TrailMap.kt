package com.elsewhere.app.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
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
private const val MAX_PTS = 9000
private const val PAD = 36f

data class TrailPin(val city: String, val x: Float, val y: Float, val first: Boolean, val alignEnd: Boolean)

/** Everything in sheet units: polylines as flat [x, y, x, y…], flights as [ax, ay, cx, cy, bx, by]. */
data class TrailLayout(
    val lines: List<FloatArray> = emptyList(), val flights: List<FloatArray> = emptyList(),
    val pins: List<TrailPin> = emptyList(), val land: List<FloatArray> = emptyList(),
)

private fun quantile(sorted: DoubleArray, p: Double): Double = sorted[floor(p * (sorted.size - 1)).toInt()]

/**
 * Fit the whole record with a little air (1st to 99th percentile, so one stray fix can't shrink the
 * rest); flights pull the frame wide, which is the point of the poster. Equirectangular, scaled by
 * the cosine of the median latitude.
 */
fun trailLayout(data: Record, stops: List<Stop>, land: FeatureCollection?, height: Float = TRAIL_H): TrailLayout {
    val total = data.strokes.sumOf { it.pts.size }
    val step = max(1, -Math.floorDiv(-total, MAX_PTS))
    val raw = data.strokes.map { p -> p.pts.filterIndexed { i, _ -> i % step == 0 } }.filter { it.size > 1 }
    val fl = data.activities.filter { it.mode == "FLYING" && it.m > 80_000 }
    val n = raw.sumOf { it.size } + fl.size * 2
    if (n == 0) return TrailLayout()
    val lngs = DoubleArray(n); val lats = DoubleArray(n)
    var i = 0
    for (l in raw) for (p in l) { lngs[i] = p.lng; lats[i] = p.lat; i++ }
    for (a in fl) { lngs[i] = a.sLng; lats[i] = a.sLat; i++; lngs[i] = a.eLng; lats[i] = a.eLat; i++ }
    val sortedLats = lats.sortedArray()
    val k = cos(quantile(sortedLats, 0.5) * PI / 180)
    val xs = DoubleArray(n) { lngs[it] * k }.sortedArray()
    val x0 = quantile(xs, 0.01); val x1 = quantile(xs, 0.99)
    val y0 = quantile(sortedLats, 0.01); val y1 = quantile(sortedLats, 0.99)
    val s = min((TRAIL_W - 2 * PAD) / max(1e-6, x1 - x0), (height - 2 * PAD) / max(1e-6, y1 - y0))
    val ox = (TRAIL_W - (x1 - x0) * s) / 2; val oy = (height - (y1 - y0) * s) / 2
    fun px(lng: Double) = ((lng * k - x0) * s + ox).toFloat()
    fun py(lat: Double) = ((y1 - lat) * s + oy).toFloat()

    val lines = raw.map { l -> FloatArray(l.size * 2).also { out -> l.forEachIndexed { j, p -> out[j * 2] = px(p.lng); out[j * 2 + 1] = py(p.lat) } } }
    val flights = fl.map { a ->
        val ax = px(a.sLng); val ay = py(a.sLat); val bx = px(a.eLng); val by = py(a.eLat)
        val dx = bx - ax; val dy = by - ay; val len = max(1e-6f, hypot(dx, dy))
        // Control point lifts the arc toward the top of the sheet, like a route drawn by hand.
        val cx = (ax + bx) / 2 - (dy / len) * len * 0.22f * (sign(dx).takeIf { it != 0f } ?: 1f)
        val cy = (ay + by) / 2 - abs(dx / len) * len * 0.22f
        floatArrayOf(ax, ay, cx, cy, bx, by)
    }
    // Cities that would print on top of one another keep only the one with more nights.
    val placed = ArrayList<TrailPin>()
    for ((idx, st) in stops.take(9).withIndex()) {
        val x = px(st.lng); val y = py(st.lat)
        val clash = placed.any { q -> abs(q.x - x) < (q.city.length + st.city.length) * 7 + 24 && abs(q.y - y) < 24 }
        if (!clash) placed.add(TrailPin(st.city, x, y, idx == 0, x > TRAIL_W - 160))
    }
    val landRings = land?.rings?.map { r -> FloatArray(r.size).also { out -> var j = 0; while (j + 1 < r.size) { out[j] = px(r[j]); out[j + 1] = py(r[j + 1]); j += 2 } } } ?: emptyList()
    return TrailLayout(lines, flights, placed.take(7), landRings)
}

private val SheetFill = Color(0xFFE2D6B8)
private val Coast = Color(0xFFB9AE92)

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
        val roads = Path().apply {
            for (l in layout.lines) { var j = 0; while (j + 1 < l.size) { if (j == 0) moveTo(l[0], l[1]) else lineTo(l[j], l[j + 1]); j += 2 } }
        }
        val flights = Path().apply {
            for (f in layout.flights) { moveTo(f[0], f[1]); quadraticTo(f[2], f[3], f[4], f[5]) }
        }
        Triple(land, roads, flights)
    }
    // The web sets names in sheet units; on a phone the sheet is a third the size, so names keep a readable sp size instead.
    val labelStyle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 0.04.em, lineHeight = 1.em)
    Canvas(
        modifier.fillMaxWidth().aspectRatio(TRAIL_W / height).background(SheetFill).border(2.dp, Poster.Ink)
            .semantics { contentDescription = "Map of the record" },
    ) {
        val u = size.width / TRAIL_W
        scale(u, u, pivot = Offset.Zero) {
            drawPath(paths.first, Poster.Stock, style = Fill)
            drawPath(paths.first, Coast, style = Stroke(0.8f))
            drawPath(paths.second, Poster.Red, style = Stroke(3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 7f))))
            drawPath(paths.third, Poster.Ink, style = Stroke(1.6f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 6f))))
            for (p in layout.pins) {
                val c = Offset(p.x, p.y)
                drawCircle(Poster.Red, if (p.first) 7f else 5f, c)
                drawCircle(Poster.Stock, if (p.first) 7f else 5f, c, style = Stroke(2.5f))
            }
        }
        for (p in layout.pins) {
            val text = measurer.measure(p.city.uppercase(), labelStyle)
            val gap = 9f * u
            val x = if (p.alignEnd) p.x * u - gap - text.size.width else p.x * u + gap
            val top = Offset(x, p.y * u - text.size.height / 2f)
            // The web halos the name with a stock stroke; a stock chip under it reads the same over the dots.
            val pad = 2.dp.toPx()
            drawRoundRect(Poster.Stock, Offset(top.x - pad, top.y), Size(text.size.width + 2 * pad, text.size.height.toFloat()), CornerRadius(pad), alpha = 0.85f)
            drawText(text, Poster.Ink, top)
        }
    }
}
