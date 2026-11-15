package com.elsewhere.app.ui.tabs.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.analytics.Cat
import com.elsewhere.app.data.Located
import com.elsewhere.app.data.Role as PlaceRole
import com.elsewhere.app.map.Cam
import com.elsewhere.app.map.ClusterPin
import com.elsewhere.app.map.Dot
import com.elsewhere.app.map.LABEL_PX
import com.elsewhere.app.map.MapHost
import com.elsewhere.app.map.Pin
import com.elsewhere.app.map.PinLayers
import com.elsewhere.app.map.PinOpts
import com.elsewhere.app.map.PinSummary
import com.elsewhere.app.map.SizeBy
import com.elsewhere.app.map.Tap
import com.elsewhere.app.map.clusterRadius
import com.elsewhere.app.map.rememberMapHost
import com.elsewhere.app.map.summary
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterTextField
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.hours
import com.elsewhere.app.ui.world.n0
import com.elsewhere.app.ui.world.plural
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

// Port of web/src/map/Pins.tsx (PinHud) over MapView.tsx's pin pass: every stop in the window as a pin.

private val FIT_PAD = 48.dp

/** What the chrome can do; the tab binds these to the view model and the host, the snapshot to no-ops. */
data class MapActions(
    val toggle: (Cat) -> Unit = {}, val only: (Cat) -> Unit = {}, val showAll: () -> Unit = {},
    val search: (String) -> Unit = {}, val select: (String?) -> Unit = {}, val sizeBy: (SizeBy) -> Unit = {}, val fit: () -> Unit = {},
    /** The open pin wants its name and photo. */
    val seen: (String) -> Unit = {},
)

@Composable
fun MapTab(vm: MapViewModel = hiltViewModel()) {
    val layers by vm.layers.collectAsStateWithLifecycle()
    val opts by vm.opts.collectAsStateWithLifecycle()
    val basemap by vm.basemap.collectAsStateWithLifecycle()
    val tilesError by vm.tilesError.collectAsStateWithLifecycle()
    val copyright by vm.copyright.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val host = rememberMapHost(basemap)
    LaunchedEffect(tilesError) { tilesError?.let { snackbar.showSnackbar("Google refused the map tiles — using the free map. $it") } }
    LaunchedEffect(host) { host.viewport.collect { it?.let(vm::idle) } }
    val density = LocalDensity.current
    val fit = { l: PinLayers, o: PinOpts -> l.fitBounds(o)?.let { host.fit(it, with(density) { FIT_PAD.roundToPx() }) } != null }

    // Framed once, when the screen first has pins, and again only on "Fit pins": a range change under the
    // user swaps the pins but leaves the camera where they put it.
    var fitted by remember(host) { mutableStateOf(false) }
    // An empty window has nothing to frame yet; the latch waits for the first fit that actually happens.
    LaunchedEffect(host, layers) { val l = layers; if (!fitted && l != null && fit(l, vm.opts.value)) fitted = true }

    LaunchedEffect(host) {
        host.taps.collect { tap ->
            val l = vm.layers.value ?: return@collect
            when (val hit = hitTest(host, l, vm.opts.value, tap, density.density)) {
                is Hit.Cluster -> host.moveTo(Cam(hit.cluster.lat, hit.cluster.lng, min(18f, host.camera.value.zoom + 2.5f)), animateMs = 400)
                is Hit.Stop -> vm.select(hit.dot.id)
                null -> vm.select(null)
            }
        }
    }

    val acts = MapActions(vm::toggle, vm::only, vm::showAll, vm::search, vm::select, vm::sizeBy, fit = { layers?.let { fit(it, opts) } }, seen = vm::seen)
    MapScreen(layers, opts, acts, snackbar = snackbar) { m ->
        host.Content(m, basemap, copyright) {
            layers?.let { PinCanvas(host, it, opts, Modifier.fillMaxSize()) }
        }
    }
}

/** Chips and search above the map, the pin HUD below it, the open pin's card over its bottom edge. */
@Composable
fun MapScreen(
    layers: PinLayers?, opts: PinOpts, acts: MapActions, modifier: Modifier = Modifier,
    snackbar: SnackbarHostState? = null, map: @Composable BoxScope.(Modifier) -> Unit,
) {
    val shown = remember(layers, opts.hidden, opts.query) { layers?.shown(opts) ?: emptyList() }
    val sum = remember(shown) { summary(shown) }
    Column(modifier.fillMaxSize().background(Poster.Stock)) {
        CatChips(layers?.counts ?: emptyMap(), opts.hidden, acts)
        PosterTextField(opts.query, acts.search, Modifier.padding(horizontal = 12.dp), placeholder = "Find a stop…")
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().weight(1f).border(2.dp, Poster.Ink)) {
            map(Modifier.fillMaxSize().padding(2.dp))
            val sel = opts.selected?.let { id -> layers?.all?.firstOrNull { it.id == id } ?: layers?.homes?.firstOrNull { it.id == id } }
            if (sel != null) {
                LaunchedEffect(sel.id) { if (sel.place.name == null || sel.place.photo == null) acts.seen(sel.id) }
                PinCard(sel, Modifier.align(Alignment.BottomCenter).padding(12.dp)) { acts.select(null) }
            }
            if (snackbar != null) SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
        PinHud(sum, opts.sizeBy, acts)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatChips(counts: Map<Cat, Int>, hidden: Set<Cat>, acts: MapActions) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = "Kinds of stop" },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        for (c in Cat.entries) {
            val n = counts[c] ?: 0
            if (n == 0) continue
            val on = c !in hidden
            Row(
                Modifier.background(if (on) Poster.Ink else Poster.Stock).border(2.dp, Poster.Ink)
                    // Tap toggles the kind, a long press keeps only it: the web's click and double-click.
                    .combinedClickable(role = Role.Checkbox, onClick = { acts.toggle(c) }, onLongClick = { acts.only(c) })
                    .semantics { contentDescription = c.label; toggleableState = if (on) ToggleableState.On else ToggleableState.Off }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(9.dp).background(Color(android.graphics.Color.parseColor(c.color))).border(1.dp, if (on) Poster.Stock else Poster.Ink))
                Spacer(Modifier.width(6.dp))
                Text(c.label.uppercase(), color = if (on) Poster.Stock else Poster.Ink, style = ChipStyle, softWrap = false)
                Spacer(Modifier.width(6.dp))
                Text(n0(n), color = if (on) Poster.Mustard else Poster.Red, style = ChipStyle)
            }
        }
        if (hidden.isNotEmpty()) Text(
            "SHOW ALL", color = Poster.Ink, style = ChipStyle,
            modifier = Modifier.border(2.dp, Poster.Ink).clickable(role = Role.Button, onClick = acts.showAll).padding(horizontal = 9.dp, vertical = 6.dp),
        )
    }
}

private val ChipStyle get() = PosterType.Display.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.03.em, lineHeight = 1.em)

@Composable
private fun PinHud(sum: PinSummary, sizeBy: SizeBy, acts: MapActions) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "${n0(sum.stops)} ${plural(sum.stops, "stop")} · ${sum.cities} ${plural(sum.cities, "city", "cities")} · ${sum.countries} ${plural(sum.countries, "country", "countries")}",
                color = Poster.Ink, style = PosterType.Label.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SIZE BY", color = Poster.Ink3, style = PosterType.Caps.copy(fontSize = 10.sp))
                for (s in SizeBy.entries) {
                    val on = s == sizeBy
                    Text(
                        s.label.uppercase(), color = if (on) Poster.Stock else Poster.Ink, style = ChipStyle.copy(fontSize = 13.sp),
                        modifier = Modifier.background(if (on) Poster.Ink else Poster.Stock).border(1.dp, Poster.Ink)
                            .selectable(selected = on, role = Role.RadioButton) { acts.sizeBy(s) }
                            .semantics { contentDescription = "Size by ${s.label}" }.padding(horizontal = 7.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        InkButton("Fit pins", acts.fit, quiet = true)
    }
}

/** The open pin's tag: what and where, two figures, a way to close it. */
@Composable
private fun PinCard(d: Dot, modifier: Modifier = Modifier, onClose: () -> Unit) {
    Row(
        modifier.fillMaxWidth().background(Color.White).border(2.dp, Poster.Ink).padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            val kind = when (d.place.role) { PlaceRole.HOME -> "Home"; PlaceRole.WORK -> "Work"; null -> d.place.primaryType?.replace('_', ' ') ?: d.cat.label }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(Color(android.graphics.Color.parseColor(d.ink))).border(1.dp, Poster.Ink))
                Spacer(Modifier.width(6.dp))
                Text(kind.uppercase(), color = Poster.Ink3, style = PosterType.Caps.copy(fontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(d.title.ifEmpty { "Unnamed stop" }.uppercase(), color = Poster.Ink, style = PosterType.Display.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 0.95.em), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(d.place.address ?: d.place.city, d.place.countryCode).joinToString(" · ").ifEmpty { "city not on record" } +
                    "  ·  ${n0(d.place.nVisits)}× · ${hours(d.place.totalHours)}",
                color = Poster.Ink3, style = PosterType.Label.copy(fontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "×", color = Poster.Ink, style = PosterType.Display.copy(fontSize = 28.sp),
            modifier = Modifier.clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = "Close" }.padding(horizontal = 8.dp),
        )
    }
}

// ---- Pins on the map -------------------------------------------------------------------------------------------

/**
 * One Canvas over whichever host is up, placed through its projection. A per-marker composable (Google's
 * MarkerComposable) or MapLibre symbol layers would mean two renderers and sprite bitmaps for the teardrop; one
 * overlay redrawn from `host.camera` on every move or zoom frame keeps hundreds of pins in step on both, and the
 * clusters behind it are regridded only when the half-zoom bucket changes.
 */
@Composable
private fun PinCanvas(host: MapHost, layers: PinLayers, opts: PinOpts, modifier: Modifier = Modifier) {
    val cam by host.camera.collectAsState()
    val measurer = rememberTextMeasurer()
    val shapes = remember { PinShapes() }
    // Measured once per text and half-point size; cleared when the cluster bucket changes so a long session never grows it.
    val texts = remember { HashMap<String, TextLayoutResult>() }
    val textsBucket = remember { doubleArrayOf(Double.NaN) }
    val labelStyle = PosterType.Label.copy(fontSize = LABEL_PX.sp, fontWeight = FontWeight.Bold, color = Poster.Ink, letterSpacing = 0.02.em)
    val countStyle = PosterType.Label.copy(fontWeight = FontWeight.Bold, color = Poster.Stock)
    Canvas(modifier) {
        val scene = layers.scene(cam.zoom.toDouble(), opts)
        val k = scene.k; val d = density
        val bucket = floor(cam.zoom * 2.0) / 2
        if (bucket != textsBucket[0]) { textsBucket[0] = bucket; texts.clear() }
        val countSize = (scene.countSize * 2).roundToInt() / 2f
        fun at(l: Located) = host.project(l.lat, l.lng)
        fun text(key: String, build: () -> TextLayoutResult) = texts.getOrPut(key, build)
        for (c in scene.clusters) {
            val o = at(c) ?: continue
            val r = clusterRadius(c, k) * d
            drawCircle(Poster.Ink, r, o); drawCircle(Poster.Stock, r, o, style = Stroke(2 * d))
            val t = text("${c.count}@$countSize") { measurer.measure(c.count.toString(), countStyle.copy(fontSize = countSize.sp)) }
            drawText(t, topLeft = Offset(o.x - t.size.width / 2f, o.y - t.size.height / 2f))
        }
        for (p in scene.pins) {
            val o = at(p.dot) ?: continue
            pin(shapes, o, (p.size + 4 * k) * d, Poster.Ink); pin(shapes, o, p.size * d, p.dot.color(), hole = true)
        }
        for (h in scene.homes) {
            val o = at(h.dot) ?: continue
            house(shapes, o, 26 * k * d, Poster.Ink); house(shapes, o, 20 * k * d, Poster.Red)
        }
        for (l in scene.labels) {
            val o = at(l.dot) ?: continue
            val t = text(l.text) { measurer.measure(l.text, labelStyle) }
            val tl = Offset(o.x + l.dx * d, o.y + l.dy * d - t.size.height / 2f)
            drawText(t, color = Poster.Stock.copy(alpha = 0.92f), topLeft = tl, drawStyle = Stroke(4 * d))
            drawText(t, topLeft = tl)
        }
        scene.selected?.let { s ->
            val o = at(s.pin.dot) ?: return@let
            drawCircle(Poster.Stock.copy(alpha = 170 / 255f), s.haloRadius * d, o); drawCircle(Poster.Ink, s.haloRadius * d, o, style = Stroke(2 * d))
            if (s.pin.home) { house(shapes, o, s.backSize * d, Poster.Ink); house(shapes, o, s.topSize * d, Poster.Red) }
            else { pin(shapes, o, s.backSize * d, Poster.Ink); pin(shapes, o, s.topSize * d, s.pin.dot.color(), hole = true) }
        }
    }
}

private fun Dot.color() = Color(android.graphics.Color.parseColor(ink))

/** The web's SVG markers in a 32-unit box: a teardrop with its tip at (16, 32) and a house centred on (16, 16). */
private class PinShapes {
    val pin: Path = Path().apply {
        moveTo(16f, 1f); cubicTo(9.9f, 1f, 5f, 5.9f, 5f, 12f); relativeCubicTo(0f, 8f, 11f, 19f, 11f, 19f)
        relativeCubicTo(11f, -11f, 11f, -19f, 11f, -19f); relativeCubicTo(0f, -6.1f, -4.9f, -11f, -11f, -11f); close()
    }
    val house: Path = Path().apply {
        moveTo(16f, 3f); lineTo(2f, 15f); relativeLineTo(4f, 0f); relativeLineTo(0f, 13f); relativeLineTo(8f, 0f); relativeLineTo(0f, -8f)
        relativeLineTo(4f, 0f); relativeLineTo(0f, 8f); relativeLineTo(8f, 0f); lineTo(26f, 15f); relativeLineTo(4f, 0f); close()
    }
}

private fun DrawScope.pin(s: PinShapes, tip: Offset, size: Float, color: Color, hole: Boolean = false) {
    val u = size / 32f
    withTransform({ translate(tip.x - size / 2, tip.y - size); scale(u, u, Offset.Zero) }) { drawPath(s.pin, color) }
    if (hole) drawCircle(Poster.Stock, 4.5f * u, Offset(tip.x, tip.y - size + 12 * u))
}

private fun DrawScope.house(s: PinShapes, centre: Offset, size: Float, color: Color) {
    val u = size / 32f
    withTransform({ translate(centre.x - size / 2, centre.y - size / 2); scale(u, u, Offset.Zero) }) { drawPath(s.house, color) }
}

private sealed interface Hit {
    data class Cluster(val cluster: ClusterPin) : Hit
    data class Stop(val dot: Dot) : Hit
}

/** What a tap landed on: a cluster disc, else the nearest pin whose icon covers the finger (a 16dp floor). */
private fun hitTest(host: MapHost, layers: PinLayers, opts: PinOpts, tap: Tap, d: Float): Hit? {
    val at = host.project(tap.lat, tap.lng) ?: return null
    val scene = layers.scene(host.camera.value.zoom.toDouble(), opts)
    for (c in scene.clusters) {
        val o = host.project(c.lat, c.lng) ?: continue
        if (hypot(o.x - at.x, o.y - at.y) <= clusterRadius(c, scene.k) * d + 8 * d) return Hit.Cluster(c)
    }
    var best: Pin? = null; var bestDist = Float.MAX_VALUE
    for (p in scene.pins + scene.homes) {
        val o = host.project(p.dot.lat, p.dot.lng) ?: continue
        val cy = if (p.home) o.y else o.y - p.size * d / 2
        val dist = hypot(o.x - at.x, cy - at.y)
        if (dist <= maxOf(p.size * d / 2, 16 * d) && dist < bestDist) { best = p; bestDist = dist }
    }
    return best?.let { Hit.Stop(it.dot) }
}
