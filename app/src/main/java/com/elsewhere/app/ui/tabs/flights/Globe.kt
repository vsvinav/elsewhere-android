package com.elsewhere.app.ui.tabs.flights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.analytics.Hop
import com.elsewhere.app.analytics.Port
import com.elsewhere.app.data.FeatureCollection
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import kotlin.math.min
import kotlin.math.sqrt

// Port of the globe mode of web/src/world/FlightMap.tsx onto a plain Canvas: no deck.gl, an orthographic
// projection of the land, the routes as great circles, every airport a dot with its town beside it.

private val RIM = 2.dp
private val TAP_REACH = 24.dp
/** Airports further than this from the centre sit too near the limb to label (web: dot product < 0.25). */
private const val LABEL_LIMB = 75.0
private const val ARC_SAMPLES = 64

/**
 * The turning earth. `rotation` is the point facing the viewer; a drag asks for a new one through
 * `onRotate`. A tap near a route asks for it through `onSelect`, a tap on nothing asks for null.
 */
@Composable
fun Globe(
    hops: List<Hop>, ports: List<Port>, land: FeatureCollection?, rotation: Rotation, onRotate: (Rotation) -> Unit,
    selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val rimPx = with(density) { RIM.toPx() }
    val view = remember(size, rimPx) { GlobeView(size.width / 2.0, size.height / 2.0, (min(size.width, size.height) / 2.0 - rimPx).coerceAtLeast(1.0)) }

    val landPath = remember(land, rotation, view) {
        Path().apply {
            for (ring in land?.rings ?: emptyList()) for (run in clipRing(ring, rotation, view)) {
                var j = 0
                while (j + 1 < run.size) { if (j == 0) moveTo(run[0], run[1]) else lineTo(run[j], run[j + 1]); j += 2 }
                close()
            }
        }
    }
    // One sampled, projected arc per hop; a null is a point behind the globe where the stroke breaks.
    val arcs = remember(hops, rotation, view) {
        hops.map { h -> greatCircle(h.s, h.t, ARC_SAMPLES).map { p -> project(p[0], p[1], rotation, view) } }
    }
    val visible = remember(arcs) { arcs.map { a -> a.filterNotNull() } }
    val dots = remember(ports, rotation, view) { ports.map { p -> project(p.p.lng, p.p.lat, rotation, view) } }

    val rot by rememberUpdatedState(rotation)
    val turn by rememberUpdatedState(onRotate)
    val measurer = rememberTextMeasurer()
    val labelStyle = PosterType.Display.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.em, color = Poster.Ink)
    // Label layout does not depend on rotation, so it is measured once per port list, not per drag frame.
    val labels = remember(ports, measurer, labelStyle) { ports.map { p -> if (p.name.isEmpty()) null else measurer.measure(p.name.uppercase(), labelStyle) } }
    val maxN = hops.maxOfOrNull { it.n } ?: 1
    val reachPx = with(density) { TAP_REACH.toPx() }.toDouble()

    Canvas(
        modifier.fillMaxWidth().aspectRatio(1f).onSizeChanged { size = it }
            .semantics { contentDescription = "${hops.size} flight routes between ${ports.size} airports" }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    turn(dragged(rot, drag.x.toDouble(), drag.y.toDouble(), min(this.size.width, this.size.height) / 2.0))
                }
            }
            .pointerInput(visible) { detectTapGestures { pos -> onSelect(nearestArc(visible, pos.x.toDouble(), pos.y.toDouble(), reachPx)) } },
    ) {
        val c = Offset(view.cx.toFloat(), view.cy.toFloat()); val r = view.r.toFloat()
        drawCircle(Poster.Stock, r, c)
        drawPath(landPath, Poster.Stock3)
        drawPath(landPath, Poster.Ink, style = Stroke(1.dp.toPx()))

        fun stroke(i: Int, color: Color, width: Float) {
            val path = Path(); var pen = false
            for (p in arcs[i]) {
                if (p == null) { pen = false; continue }
                if (pen) path.lineTo(p[0].toFloat(), p[1].toFloat()) else path.moveTo(p[0].toFloat(), p[1].toFloat())
                pen = true
            }
            drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        fun dotRadius(n: Int) = (3f + 1.5f * sqrt(n.toFloat())).coerceAtMost(8f).dp.toPx()
        hops.forEachIndexed { i, h -> if (i != selected) stroke(i, Poster.Red, (1.5f + 2.5f * h.n / maxN).dp.toPx()) }
        // The open route prints last, in mustard on an ink key line, so it reads over the others.
        selected?.takeIf { it in hops.indices }?.let { i ->
            val w = (3f + 2.5f * hops[i].n / maxN).dp.toPx()
            stroke(i, Poster.Ink, w + 3.dp.toPx()); stroke(i, Poster.Mustard, w)
        }

        ports.forEachIndexed { i, p ->
            val d = dots[i] ?: return@forEachIndexed
            val rad = dotRadius(p.n)
            drawCircle(Poster.Stock, rad, Offset(d[0].toFloat(), d[1].toFloat()))
            drawCircle(Poster.Ink, rad, Offset(d[0].toFloat(), d[1].toFloat()), style = Stroke(2.dp.toPx()))
        }

        // Labels sit beside their dot; one that would overlap a placed label slides to another side, and
        // one with no room at all waits, as on the web's globe. Names near the limb are skipped.
        val kept = ArrayList<Rect>()
        fun inDisc(o: Offset) = (o - c).getDistance() <= r - rimPx
        ports.forEachIndexed { i, p ->
            val text = labels[i] ?: return@forEachIndexed
            val d = dots[i] ?: return@forEachIndexed
            if (angleBetween(rotation, p.p.lng, p.p.lat) > LABEL_LIMB) return@forEachIndexed
            val w = text.size.width.toFloat(); val h = text.size.height.toFloat()
            val x = d[0].toFloat(); val y = d[1].toFloat()
            val gap = dotRadius(p.n) + 4.dp.toPx()
            val slots = listOf(
                Offset(x + gap, y - h / 2), Offset(x - gap - w, y - h / 2),
                Offset(x + gap, y - h - gap), Offset(x + gap, y + gap), Offset(x - gap - w, y - h - gap), Offset(x - gap - w, y + gap),
            )
            val box = slots.map { Rect(it, Size(w, h)) }
                .firstOrNull { b -> inDisc(b.topLeft) && inDisc(b.topRight) && inDisc(b.bottomLeft) && inDisc(b.bottomRight) && kept.none { it.overlaps(b) } }
                ?: return@forEachIndexed
            kept.add(box)
            drawText(text, topLeft = box.topLeft)
        }

        drawCircle(Poster.Ink, r, c, style = Stroke(rimPx))
    }
}
