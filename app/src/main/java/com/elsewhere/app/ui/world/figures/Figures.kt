package com.elsewhere.app.ui.world.figures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.analytics.Figure
import com.elsewhere.app.analytics.Night
import com.elsewhere.app.analytics.NightKind
import com.elsewhere.app.data.FeatureCollection
import com.elsewhere.app.ui.world.DisplayFamily
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import java.time.LocalDate
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Port of web/src/world/figures/Figures.tsx: each SVG becomes a Canvas drawn in the SVG's own
// unit box, with text laid over it at the same coordinates.

/** Country outlines for the globe; the app root provides them once loaded. */
val LocalLand = compositionLocalOf<FeatureCollection?> { null }

@Composable
fun Fig(f: Figure, modifier: Modifier = Modifier) = when (f) {
    is Figure.Ring -> Ring(f.pct, f.label, modifier)
    is Figure.Scale -> Scale(f.value, f.ref, f.refLabel, f.unit, f.valueLabel ?: "You", modifier)
    is Figure.Dial -> Dial(f.hours, f.hand, f.arc, f.label, modifier)
    is Figure.Week -> Week(f.cells, modifier)
    is Figure.Globe -> Globe(f.lat, f.lng, f.homeLat, f.homeLng, modifier)
    is Figure.Beds -> Beds(f.nights, modifier)
}

private fun polar(cx: Float, cy: Float, r: Float, frac: Double): Offset =
    Offset(cx + r * sin(2 * PI * frac).toFloat(), cy - r * cos(2 * PI * frac).toFloat())

private fun n(x: Double): String = String.format(Locale.US, "%,d", Math.round(x))

/** The SVG `.l` text: 9px body caps in ink-3. */
private val SmallLabel = PosterType.Caps.copy(letterSpacing = 0.12.em, lineHeight = 1.em)
/** The SVG `.n` text: display numerals. */
private val Numeral = PosterType.Display.copy(letterSpacing = (-0.01).em, lineHeight = 1.em)

/** Places a child so its centre lands on (x, y) in unit space, like an SVG `text-anchor="middle"` at the glyph's middle. */
private fun Modifier.centerAt(x: Float, y: Float, u: Float) = layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) { p.place((x * u - p.width / 2f).roundToInt(), (y * u - p.height / 2f).roundToInt()) }
}

/** A square figure drawn in a `view`-unit box. `u` is pixels per unit; `sp(units)` converts an SVG font size. */
@Composable
private fun UnitBox(view: Rect, modifier: Modifier, content: @Composable (u: Float, sp: (Float) -> TextUnit) -> Unit) {
    BoxWithConstraints(modifier.aspectRatio(view.width / view.height)) {
        val density = LocalDensity.current
        val u = with(density) { maxWidth.toPx() } / view.width
        val sp = { units: Float -> with(density) { (units * u).toSp() } }
        content(u, sp)
    }
}

private fun DrawScope.inUnits(view: Rect, block: DrawScope.() -> Unit) {
    val u = size.width / view.width
    scale(u, u, pivot = Offset.Zero) { translate(-view.left, -view.top) { block() } }
}

/** Share of a whole: a thin ghost ring with the filled arc in red. A floor on the arc so 3% still reads. */
@Composable
fun Ring(pct: Double, label: String, modifier: Modifier = Modifier) {
    val view = Rect(0f, 0f, 140f, 140f)
    val r = 54f
    val p = pct.coerceIn(0.0, 100.0)
    val sweep = 360f * max(0.015f, (p / 100).toFloat())
    UnitBox(view, modifier) { u, sp ->
        Canvas(Modifier.fillMaxSize()) {
            inUnits(view) {
                drawCircle(Poster.Ghost, r, Offset(70f, 70f), style = Stroke(10f))
                drawArc(Poster.Red, -90f, sweep, false, Offset(70f - r, 70f - r), Size(2 * r, 2 * r), style = Stroke(10f))
            }
        }
        val num = if (pct > 0 && pct < 1) String.format(Locale.US, "%.1f", pct) else Math.round(pct).toString()
        Text(
            buildAnnotatedString { append(num); withStyle(SpanStyle(fontSize = sp(15f))) { append("%") } },
            Modifier.centerAt(70f, 55f, u), color = Poster.Ink, style = Numeral.copy(fontSize = sp(30f)), maxLines = 1,
        )
        Text(label.uppercase(), Modifier.centerAt(70f, 83f, u), color = Poster.Ink3, style = SmallLabel.copy(fontSize = sp(9f)), maxLines = 1)
    }
}

/** Two bars on one scale: what you did against something you can picture. */
@Composable
fun Scale(value: Double, ref: Double, refLabel: String, unit: String, valueLabel: String = "You", modifier: Modifier = Modifier) {
    val mx = max(value, ref).takeIf { it > 0 } ?: 1.0
    val w = { x: Double -> max(0.015f, (x / mx).toFloat()) }
    val style = PosterType.Caps.copy(fontSize = 12.sp, letterSpacing = 0.14.em, lineHeight = 1.em)
    @Composable fun row(lab: String, frac: Float, color: Color, v: String) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(lab.uppercase(), Modifier.width(76.dp), color = Poster.Ink3, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.weight(1f)) { Box(Modifier.fillMaxWidth(frac).height(22.dp).background(color)) }
            Text(v.uppercase(), color = Poster.Ink, style = style, maxLines = 1)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        row(valueLabel, w(value), Poster.Red, "${n(value)} $unit")
        row(refLabel, w(ref), Poster.Ink, "${n(ref)} $unit")
    }
}

/**
 * 24-hour clock, midnight at the top: hour ticks, a red arc for the night window, faint bars for how
 * often legs start each hour, and a hand pointing at the headline time.
 */
@Composable
fun Dial(hours: List<Int>, hand: Double, arc: Pair<Int, Int>?, label: String, modifier: Modifier = Modifier) {
    val view = Rect(-6f, -6f, 146f, 146f)
    val cx = 70f; val cy = 70f; val R = 56f
    val mx = max(1, hours.maxOrNull() ?: 1)
    UnitBox(view, modifier) { u, sp ->
        Canvas(Modifier.fillMaxSize()) {
            inUnits(view) {
                drawCircle(Poster.Ink, R, Offset(cx, cy), style = Stroke(2f))
                if (arc != null) {
                    val r = R - 4
                    val from = 360f * arc.first / 24f - 90f
                    val sweep = 360f * (arc.second - arc.first) / 24f
                    drawArc(Poster.Red, from, sweep, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r), alpha = 0.85f, style = Stroke(5f))
                }
                for (h in 0 until 24) {
                    val v = hours.getOrElse(h) { 0 }
                    val big = h % 6 == 0
                    if (v > 0) {
                        val f = (h + 0.5) / 24
                        drawLine(Poster.Ink, polar(cx, cy, 10f, f), polar(cx, cy, 10f + 22f * v / mx, f), strokeWidth = 3f, alpha = 0.18f)
                    }
                    drawLine(Poster.Ink, polar(cx, cy, R - (if (big) 10f else 6f), h / 24.0), polar(cx, cy, R - 1, h / 24.0), strokeWidth = if (big) 2.5f else 1.2f)
                }
                drawLine(Poster.Red, Offset(cx, cy), polar(cx, cy, R - 14, hand / 24), strokeWidth = 3.5f, cap = StrokeCap.Round)
                drawCircle(Poster.Red, 4f, Offset(cx, cy))
            }
        }
        for (h in listOf(0, 6, 12, 18)) {
            val p = polar(cx, cy, R + 10, h / 24.0)
            Text(
                h.toString().padStart(2, '0'), Modifier.centerAt(p.x - view.left, p.y - view.top, u),
                color = Poster.Ink3, style = SmallLabel.copy(fontSize = sp(9f)), maxLines = 1,
            )
        }
        Text(
            label.uppercase(), Modifier.centerAt(cx - view.left, cy + 38 - view.top, u),
            color = Poster.Ink, style = Numeral.copy(fontSize = sp(16f)), maxLines = 1,
        )
    }
}

/** Seven columns, Monday first; the weekend in red. */
@Composable
fun Week(cells: List<Int>, modifier: Modifier = Modifier) {
    val mx = max(1, cells.maxOrNull() ?: 1)
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(modifier.height(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEachIndexed { i, d ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                val frac = max(0.03f, cells.getOrElse(i) { 0 }.toFloat() / mx)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(frac).background(if (i >= 5) Poster.Red else Poster.Ink))
                }
                Spacer(Modifier.height(6.dp))
                Text(d, color = Poster.Ink3, style = SmallLabel.copy(fontSize = 11.sp), textAlign = TextAlign.Center)
            }
        }
    }
}

/** One cell per night, stacked by week with Monday on top: ink home, red new city, mustard repeat, outline unknown. */
@Composable
fun Beds(nights: List<Night>, modifier: Modifier = Modifier, maxHeight: androidx.compose.ui.unit.Dp = 84.dp) {
    if (nights.isEmpty()) return
    val pad = (LocalDate.parse(nights[0].date).dayOfWeek.value + 6) % 7
    val cells: List<Night?> = List<Night?>(pad) { null } + nights
    val weeks = (cells.size + 6) / 7
    val vw = weeks * 8f; val vh = 56f
    // Fill the width, but never taller than `maxHeight`: the web's `width: 100%; max-height: 84px; meet`.
    Canvas(modifier.fillMaxWidth().heightIn(max = maxHeight).aspectRatio(vw / vh, matchHeightConstraintsFirst = true)) {
        val u = min(size.width / vw, size.height / vh)
        scale(u, u, pivot = Offset.Zero) {
            cells.forEachIndexed { i, x ->
                if (x == null) return@forEachIndexed
                val col = (i / 7) * 8f; val row = (i % 7) * 8f
                if (x.kind != NightKind.NONE) {
                    val color = when (x.kind) { NightKind.HOME -> Poster.Ink; NightKind.NEW -> Poster.Red; else -> Poster.Mustard }
                    drawRect(color, Offset(col + 0.5f, row + 0.5f), Size(7f, 7f))
                } else {
                    drawRect(Poster.Ghost, Offset(col + 1f, row + 1f), Size(6f, 6f), style = Stroke(0.6f))
                }
            }
        }
    }
}

private const val RAD = PI / 180

private fun toVec(lo: Double, la: Double) = doubleArrayOf(cos(la * RAD) * cos(lo * RAD), cos(la * RAD) * sin(lo * RAD), sin(la * RAD))

/** Point `t` of the way along the great circle from a to b, as [lng, lat] degrees. */
internal fun interpolate(aLng: Double, aLat: Double, bLng: Double, bLat: Double, t: Double): Pair<Double, Double> {
    val va = toVec(aLng, aLat); val vb = toVec(bLng, bLat)
    val x = va[0] * (1 - t) + vb[0] * t; val y = va[1] * (1 - t) + vb[1] * t; val z = va[2] * (1 - t) + vb[2] * t
    return (atan2(y, x) / RAD) to (atan2(z, hypot(x, y)) / RAD)
}

/** Small orthographic globe facing the midpoint of home and the far point, the arc between them in red. */
@Composable
fun Globe(lat: Double, lng: Double, homeLat: Double, homeLng: Double, modifier: Modifier = Modifier, land: FeatureCollection? = LocalLand.current) {
    val R = 64f; val cx = 70f; val cy = 70f
    val (c0d, c1d) = interpolate(homeLng, homeLat, lng, lat, 0.5)
    val c0 = c0d * RAD; val c1 = c1d * RAD
    // Orthographic projection; the third value is whether the point faces us.
    fun proj(lo: Double, la: Double): Triple<Float, Float, Boolean> {
        val l = lo * RAD; val p = la * RAD
        val vis = sin(c1) * sin(p) + cos(c1) * cos(p) * cos(l - c0) > 0
        val x = cx + R * cos(p) * sin(l - c0)
        val y = cy - R * (cos(c1) * sin(p) - sin(c1) * cos(p) * cos(l - c0))
        return Triple(x.toFloat(), y.toFloat(), vis)
    }
    fun ring(path: Path, coords: DoubleArray) {
        var pen = false
        var i = 0
        while (i + 1 < coords.size) {
            val (x, y, vis) = proj(coords[i], coords[i + 1])
            i += 2
            if (!vis) { pen = false; continue }
            if (pen) path.lineTo(x, y) else path.moveTo(x, y)
            pen = true
        }
    }
    Canvas(modifier.aspectRatio(1f)) {
        val u = size.width / 140f
        scale(u, u, pivot = Offset.Zero) {
            drawCircle(Poster.Ink, R, Offset(cx, cy))
            if (land != null) {
                val lp = Path()
                for (r in land.rings) ring(lp, r)
                drawPath(lp, Poster.Stock, alpha = 0.85f, style = Stroke(0.7f, join = StrokeJoin.Round))
            }
            val arc = DoubleArray(66)
            for (i in 0..32) { val (lo, la) = interpolate(homeLng, homeLat, lng, lat, i / 32.0); arc[i * 2] = lo; arc[i * 2 + 1] = la }
            val ap = Path(); ring(ap, arc)
            drawPath(ap, Poster.Red, style = Stroke(2f))
            val home = proj(homeLng, homeLat); val far = proj(lng, lat)
            drawCircle(Poster.Stock, 3f, Offset(home.first, home.second))
            drawCircle(Poster.Red, 4f, Offset(far.first, far.second))
            drawCircle(Poster.Stock, 4f, Offset(far.first, far.second), style = Stroke(1.2f))
        }
    }
}
