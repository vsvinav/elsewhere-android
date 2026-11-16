package com.elsewhere.app.ui.account

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.motionOn
import java.text.NumberFormat
import kotlin.math.roundToInt

data class Pin(val x: Float, val y: Float, val label: String, val km: Int)

val POINTS = listOf(
    Pin(0.12f, 0.78f, "HOME", 0), Pin(0.23f, 0.62f, "BOSTON", 420), Pin(0.41f, 0.36f, "REYKJAVÍK", 4260),
    Pin(0.57f, 0.47f, "LISBON", 7210), Pin(0.74f, 0.30f, "ISTANBUL", 10390), Pin(0.90f, 0.58f, "NAIROBI", 14920),
)

/** The hand's wander between each pair of pins; fixed, so the sketch prints the same every time. */
val WOBBLE = listOf(
    listOf(0.15f to 0.74f, 0.18f to 0.69f, 0.21f to 0.64f),
    listOf(0.27f to 0.55f, 0.33f to 0.46f, 0.37f to 0.40f),
    listOf(0.46f to 0.39f, 0.50f to 0.44f, 0.54f to 0.46f),
    listOf(0.62f to 0.42f, 0.66f to 0.36f, 0.70f to 0.32f),
    listOf(0.79f to 0.36f, 0.84f to 0.46f, 0.88f to 0.54f),
)

const val SKETCH_HOLD = 400L
const val SKETCH_LEG = 1200L
const val SKETCH_TOTAL = 6400L
private const val STAMP_MS = 260L
private val LEGS = POINTS.size - 1

/** Where the pen is at one instant: the leg being drawn (0 during the hold), how much of the route is down, the counter, and the stamps landed. */
data class Sketch(val leg: Int, val fraction: Float, val km: Int, val stamps: Int)

private fun ease(t: Float): Float = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f) * (-2f * t + 2f) / 2f

fun sketchAt(t: Long): Sketch {
    if (t < SKETCH_HOLD) return Sketch(0, 0f, 0, 0)
    val leg = minOf(LEGS, ((t - SKETCH_HOLD) / SKETCH_LEG).toInt() + 1)
    val eased = ease(((t - SKETCH_HOLD - SKETCH_LEG * (leg - 1)).toFloat() / SKETCH_LEG).coerceIn(0f, 1f))
    val from = POINTS[leg - 1]; val to = POINTS[leg]
    return Sketch(leg, (leg - 1 + eased) / LEGS, from.km + ((to.km - from.km) * eased).roundToInt(), if (eased >= 1f) leg else leg - 1)
}

/** The mustard stamp's size for far pin `n` (1-based): nothing before it lands, one overshoot, then rest. */
private fun stampScale(n: Int, t: Long): Float {
    val age = t - (SKETCH_HOLD + SKETCH_LEG * n)
    if (age < 0) return 0f
    if (age >= STAMP_MS || t >= SKETCH_TOTAL) return 1f
    val p = age.toFloat() / STAMP_MS
    return if (p < 0.6f) 1.18f * (p / 0.6f) else 1.18f - 0.18f * ((p - 0.6f) / 0.4f)
}

/** The welcome panel, played once when it appears; the resting frame straight away when animations are off. */
@Composable
fun WelcomeSketch(modifier: Modifier = Modifier) {
    val motion = motionOn()
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) { if (motion) progress.animateTo(1f, tween(SKETCH_TOTAL.toInt(), easing = LinearEasing)) }
    WelcomeSketch((progress.value * SKETCH_TOTAL).toLong(), modifier)
}

/** The panel at instant `t` in ms; pure, so a snapshot can print any frame. */
@Composable
fun WelcomeSketch(t: Long, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val sketch = sketchAt(t)
    val km = remember(sketch.km) { NumberFormat.getIntegerInstance().format(sketch.km) }
    Box(modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Poster.Stock)) {
        Canvas(Modifier.matchParentSize()) {
            grid()
            route(sketch)
            pins(t, measurer)
            counter(km, measurer)
        }
        Box(Modifier.align(Alignment.BottomStart).padding(12.dp).size(36.dp).background(Poster.Ink), contentAlignment = Alignment.Center) {
            DisplayText("E", 18.sp, color = Poster.Stock)
        }
    }
}

private fun DrawScope.grid() {
    val step = size.width / 12f
    val ink = Poster.Ink.copy(alpha = 0.08f)
    var x = step
    while (x < size.width) { drawLine(ink, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx()); x += step }
    var y = step
    while (y < size.height) { drawLine(ink, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); y += step }
}

private fun DrawScope.at(p: Pair<Float, Float>) = Offset(p.first * size.width, p.second * size.height)

/** Legs are not the same length, so the pen is paced leg by leg: whole legs down, then `eased` of the one in hand. */
private fun DrawScope.route(s: Sketch) {
    if (s.fraction <= 0f) return
    val path = Path().apply {
        val home = at(POINTS[0].x to POINTS[0].y); moveTo(home.x, home.y)
        for (i in 1..LEGS) { WOBBLE[i - 1].forEach { val o = at(it); lineTo(o.x, o.y) }; val o = at(POINTS[i].x to POINTS[i].y); lineTo(o.x, o.y) }
    }
    val legLengths = (1..LEGS).map { i ->
        PathMeasure().apply {
            setPath(Path().apply {
                val a = at(POINTS[i - 1].x to POINTS[i - 1].y); moveTo(a.x, a.y)
                WOBBLE[i - 1].forEach { val o = at(it); lineTo(o.x, o.y) }; val b = at(POINTS[i].x to POINTS[i].y); lineTo(b.x, b.y)
            }, false)
        }.length
    }
    val eased = s.fraction * LEGS - (s.leg - 1)
    val stop = legLengths.take(s.leg - 1).sum() + legLengths[s.leg - 1] * eased
    val drawn = Path()
    PathMeasure().apply { setPath(path, false) }.getSegment(0f, stop, drawn, true)
    drawPath(
        drawn, Poster.Red,
        style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
    )
}

private fun DrawScope.pins(t: Long, measurer: TextMeasurer) {
    val label = PosterType.Caps.copy(fontSize = 11.sp, color = Poster.Ink)
    POINTS.forEachIndexed { i, pin ->
        val c = at(pin.x to pin.y)
        val scale = if (i == 0) 1f else stampScale(i, t)
        if (scale > 0f) drawCircle(if (i == 0) Poster.Red else Poster.Mustard, 14.dp.toPx() * scale, c)
        drawCircle(Poster.Ink, 5.dp.toPx(), c)
        val text = measurer.measure(pin.label, label)
        val x = (c.x - 6.dp.toPx()).coerceIn(4.dp.toPx(), size.width - text.size.width - 4.dp.toPx())
        drawText(text, topLeft = Offset(x, c.y - 6.dp.toPx() - 5.dp.toPx() - text.size.height))
    }
}

/** The ink figure over a red plate 1.5dp off register, with its unit beneath, in the panel's bottom-right corner. */
private fun DrawScope.counter(km: String, measurer: TextMeasurer) {
    val pad = 12.dp.toPx()
    val unit = measurer.measure("KILOMETRES", PosterType.Caps.copy(fontSize = 11.sp, color = Poster.Ink))
    val figure = measurer.measure(km, PosterType.Display.copy(fontSize = 56.sp))
    val unitAt = Offset(size.width - pad - unit.size.width, size.height - pad - unit.size.height)
    val figureAt = Offset(size.width - pad - figure.size.width, unitAt.y - 4.dp.toPx() - figure.size.height)
    drawText(figure, Poster.Red, topLeft = figureAt + Offset(1.5.dp.toPx(), 1.5.dp.toPx()))
    drawText(figure, Poster.Ink, topLeft = figureAt)
    drawText(unit, topLeft = unitAt)
}
