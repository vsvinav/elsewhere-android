package com.elsewhere.app.ui.tabs.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.analytics.Lens
import com.elsewhere.app.analytics.SegmentKind
import com.elsewhere.app.analytics.isHome
import com.elsewhere.app.analytics.lensOf
import com.elsewhere.app.analytics.segmentAt
import com.elsewhere.app.analytics.whereaboutsAt
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Record
import com.elsewhere.app.map.Arrival
import com.elsewhere.app.map.Arrivals
import com.elsewhere.app.map.BIG_KM
import com.elsewhere.app.map.Eased
import com.elsewhere.app.map.Follow
import com.elsewhere.app.map.GoogleMapHost
import com.elsewhere.app.map.HEAD_REAL_S
import com.elsewhere.app.map.MapEvent
import com.elsewhere.app.map.MapHost
import com.elsewhere.app.map.PlayState
import com.elsewhere.app.map.Provider
import com.elsewhere.app.map.ReplayTrails
import com.elsewhere.app.map.Stamp
import com.elsewhere.app.map.StampHold
import com.elsewhere.app.map.Trail
import com.elsewhere.app.map.activeTrails
import com.elsewhere.app.map.rememberMapHost
import com.elsewhere.app.map.resolve
import com.elsewhere.app.map.stampOf
import com.elsewhere.app.map.trailHead
import com.elsewhere.app.map.wakeLength
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Port of web/src/map/MapScreen.tsx in Play mode over MapView.tsx's replay pass: the record runs along its trails.

/** What the chrome can do; the tab binds these to the view model, the snapshot to no-ops. */
data class ReplayActions(
    val play: () -> Unit = {}, val speed: (Double) -> Unit = {}, val scrub: (Double) -> Unit = {},
    val follow: () -> Unit = {}, val lens: (Lens) -> Unit = {},
)

@Composable
fun ReplayTab(vm: PlaybackViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val trails by vm.trails.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val s = settings; val rec = data
    if (s == null || rec == null) { Box(Modifier.fillMaxSize().background(Poster.Stock)); return }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val host = rememberMapHost(s, onGoogleLoaded = vm::googleLoaded, onGoogleFailed = {
        vm.googleFailed()
        val swapped = resolve(s, sessionDown = true) == Provider.OSM
        scope.launch { snackbar.showSnackbar(if (swapped) "Google refused the key — using OpenStreetMap" else "Google Maps didn't load") }
    })

    // Leaving the tab pops its back-stack entry and clears the view model, so the clock stops with it.
    LaunchedEffect(vm) { vm.shown() }

    val follow = remember(host) { Follow() }
    val arrivals = remember { Arrivals() }
    var tags by remember { mutableStateOf<List<Arrival>>(emptyList()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val frame = remember { mutableLongStateOf(0L) }

    // Dragging or pinching by hand hands the camera over; the Follow button takes it back.
    LaunchedEffect(host) { host.events.collect { if (it == MapEvent.DragStart) vm.setFollow(false) } }

    LaunchedEffect(host, rec) {
        var last = 0L
        while (true) withFrameNanos { t ->
            val dt = if (last == 0L) 0.0 else min(0.25, (t - last) / 1e9); last = t
            val tMs = t / 1_000_000
            vm.tick(dt)
            val st = vm.state.value
            if (st.playhead.isNaN()) return@withFrameNanos
            val now = st.playhead.toLong()
            if (st.follow && viewport != IntSize.Zero) {
                follow.tick(rec, now, st.rate, st.speed, dt, tMs, viewport.width, viewport.height, host.camera.value)?.let { host.moveTo(it) }
            } else follow.reset()
            arrivals.at(rec, now, st.playing, tMs)
            if (arrivals.prune(tMs) || arrivals.tags !== tags) tags = arrivals.tags
            frame.longValue = tMs
        }
    }

    val acts = ReplayActions(
        play = { vm.setPlaying(!state.playing) }, speed = vm::setSpeed, scrub = vm::scrub, follow = { vm.setFollow(true) }, lens = vm::setLens,
    )
    ReplayScreen(rec, state, tags, acts, snackbar = snackbar) { m ->
        host.Content(m) {
            val tr = trails
            if (tr != null) ReplayCanvas(host, rec, tr, state, frame, Modifier.fillMaxSize().onSizeChanged { viewport = it })
            if ((host as? GoogleMapHost)?.needsRestart == true) {
                Text(
                    "Restart the app to apply the new key", color = Poster.Ink,
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).background(Poster.Mustard).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Lens chips above the map, arrival tags over it, the HUD stub below. */
@Composable
fun ReplayScreen(
    data: Record, state: PlayState, tags: List<Arrival>, acts: ReplayActions, modifier: Modifier = Modifier,
    snackbar: SnackbarHostState? = null, map: @Composable BoxScope.(Modifier) -> Unit,
) {
    val range = data.meta.range
    val playhead = if (state.playhead.isNaN()) range.first.toDouble() else state.playhead
    val info = remember(data, playhead.toLong() / 60) { hudInfo(data, playhead.toLong()) }
    Column(modifier.fillMaxSize().background(Poster.Stock)) {
        LensChips(state.lens, acts.lens)
        Box(Modifier.fillMaxWidth().weight(1f).border(2.dp, Poster.Ink)) {
            map(Modifier.fillMaxSize().padding(2.dp))
            ArrivalTags(tags, data.places, Modifier.align(Alignment.TopStart).padding(10.dp))
            if (snackbar != null) SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
        ReplayHud(info, range, playhead, state.playing, state.speed, state.follow, acts.play, acts.speed, acts.scrub, acts.follow)
    }
}

// ---- The replay on the map ---------------------------------------------------------------------------------------

/** A place the replay has reached: the dots are the places you kept going back to; the Map tab pins every stop. */
private class RevealDot(val id: String, val place: Place, val title: String)

private fun revealDots(data: Record): List<RevealDot> = data.places.entries
    .filter { (_, p) -> !isHome(p) && p.nVisits >= 2 }
    .map { (id, p) -> RevealDot(id, p, (p.name ?: p.label ?: "").trim()) }
    .sortedByDescending { it.place.nVisits }

/**
 * One Canvas over whichever host is up, placed through its projection and redrawn every frame of the clock: the
 * paused record as hairlines, a dotted wake and a solid head behind the stamp, the places reached so far, and the
 * stamp itself in the ink of the current movement.
 */
@Composable
private fun ReplayCanvas(host: MapHost, data: Record, trails: ReplayTrails, state: PlayState, frame: State<Long>, modifier: Modifier = Modifier) {
    val cam by host.camera.collectAsState()
    val measurer = rememberTextMeasurer()
    val icons = remember { StampIcons() }
    val dots = remember(data) { revealDots(data) }
    val wake = remember { Eased(2000) }
    val ring = remember { Eased(180, 500) }
    val stamp = remember { StampHold() }
    val texts = remember { HashMap<String, TextLayoutResult>() }
    val labelStyle = PosterType.Label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Poster.Ink, letterSpacing = 0.02.em)
    Canvas(modifier) {
        val tMs = frame.value
        if (state.playhead.isNaN()) return@Canvas
        val now = state.playhead; val nowL = now.toLong(); val rate = state.rate; val d = density
        val lensTrails = trails.simplified(state.lens, cam.zoom.toDouble())
        fun at(lat: Double, lng: Double) = host.project(lat, lng)

        if (!state.playing) {
            // Paused: the whole record as hairlines, like the strip printed on the pass.
            for (t in lensTrails) {
                val a = if (t.lens == Lens.FLY) 140 / 255f else if (t.guessed) 50 / 255f else 90 / 255f
                polyline(t, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, ink(t.lens).copy(alpha = a), 1.5f * d, fade = false, dashed = null, ::at)
            }
        }
        // A dotted wake that fades over ~16 s of screen time, then a solid head in the leg's own ink over only the live trails.
        val headLen = HEAD_REAL_S * rate; val wakeLen = wake.at(wakeLength(rate), tMs)
        val dash = PathEffect.dashPathEffect(floatArrayOf(5 * d, 4 * d))
        for (t in activeTrails(lensTrails, now - wakeLen, now)) {
            val c = ink(t.lens).copy(alpha = (if (state.playing) 0.8f else 0.4f) * if (t.guessed) 110 / 255f else 1f)
            polyline(t, now - wakeLen, now, c, 2.5f * d, fade = true, dashed = dash, ::at)
        }
        for (t in activeTrails(lensTrails, now - headLen * 1.6, now)) {
            val c = ink(t.lens).copy(alpha = if (t.guessed) 110 / 255f else 1f)
            polyline(t, now - headLen, now, c, 6f * d, fade = true, dashed = null, ::at)
        }

        // Only places already visited exist on the map; a fresh one fades in over a second of screen time.
        val fadeSpan = max(60.0, rate * 1.2)
        val pxPerM = 256 * 2.0.pow(cam.zoom.toDouble()) / (40_075_016.686 * cos(cam.lat * PI / 180))
        val taken = ArrayList<FloatArray>()
        val labels = cam.zoom >= 6f
        for (dot in dots) {
            val p = dot.place
            if (p.firstTs > nowL) continue
            val o = at(p.lat, p.lng) ?: continue
            if (o.x < -40 || o.y < -40 || o.x > size.width + 40 || o.y > size.height + 40) continue
            val alpha = min(1.0, (now - p.firstTs) / fadeSpan).toFloat()
            val r = ((20 + sqrt(p.nVisits.toDouble()) * 12) * pxPerM).toFloat().coerceIn(2f * d, 16f * d)
            drawCircle(Poster.Ink.copy(alpha = 220 / 255f * alpha), r, o); drawCircle(Poster.Stock.copy(alpha = alpha), r, o, style = Stroke(2 * d))
            if (!labels || dot.title.isEmpty()) continue
            // Greedy screen-space culling: the most-visited label wins its box.
            val t = texts.getOrPut(dot.title) { measurer.measure(dot.title.uppercase(), labelStyle) }
            val x0 = o.x + 12 * d; val y0 = o.y - t.size.height / 2f; val x1 = x0 + t.size.width; val y1 = y0 + t.size.height
            if (taken.any { b -> x0 < b[2] && x1 > b[0] && y0 < b[3] && y1 > b[1] }) continue
            taken.add(floatArrayOf(x0, y0, x1, y1))
            drawText(t, color = Poster.Stock.copy(alpha = 0.92f * alpha), topLeft = Offset(x0, y0), drawStyle = Stroke(4 * d))
            drawText(t, color = Poster.Ink.copy(alpha = alpha), topLeft = Offset(x0, y0))
        }

        // The stamp: a stock disc with the leg's ink ring, and the vehicle inside turned the way it is going.
        val w = whereaboutsAt(data, nowL) ?: return@Canvas
        val still = w.mode == "STILL"
        val head = if (still) null else trailHead(lensTrails, now)
        val lat = head?.lat ?: w.lat; val lng = head?.lng ?: w.lng; val heading = head?.heading ?: w.heading
        val seg = if (still) null else segmentAt(data, nowL)
        val kind = stamp.kind(if (still) Stamp.STILL else stampOf(lensOf(w.mode)), rate, seg != null && seg.kind == SegmentKind.ACTIVITY && seg.km >= BIG_KM, tMs)
        val c = when (kind) { Stamp.STILL, Stamp.FF -> Poster.Ink; Stamp.FLY -> ink(Lens.FLY); Stamp.DRIVE -> ink(Lens.DRIVE); Stamp.TRANSIT -> ink(Lens.TRANSIT); Stamp.FOOT -> ink(Lens.FOOT) }
        val turns = kind == Stamp.FLY || kind == Stamp.DRIVE || kind == Stamp.FF
        val r = (ring.at(if (kind == Stamp.STILL) 11.0 else 20.0, tMs) * d).toFloat()
        val o = at(lat, lng) ?: return@Canvas
        drawCircle(Poster.Stock, r, o); drawCircle(c, r, o, style = Stroke(3 * d))
        val sz = r * 1.3f
        translate(o.x - sz / 2, o.y - sz / 2) {
            rotate(if (turns) heading.toFloat() else 0f, pivot = Offset(sz / 2, sz / 2)) {
                scale(sz / 32f, pivot = Offset.Zero) { drawPath(icons[kind], c) }
            }
        }
    }
}

/** A trail's points inside [from, to] as screen lines, the ends cut to the window so the head sits exactly at now. */
private fun DrawScope.polyline(
    t: Trail, from: Double, to: Double, color: Color, width: Float, fade: Boolean, dashed: PathEffect?, at: (Double, Double) -> Offset?,
) {
    val ts = t.ts; val path = t.path
    val span = max(1.0, to - from)
    for (i in 1 until path.size) {
        val ta = ts[i - 1]; val tb = ts[i]
        if (tb < from || ta > to) continue
        var a = path[i - 1]; var b = path[i]
        if (ta < from) { val k = (from - ta) / max(1e-9, tb - ta); a = LatLng(a.lat + (b.lat - a.lat) * k, a.lng + (b.lng - a.lng) * k) }
        if (tb > to) { val k = (to - ta) / max(1e-9, tb - ta); b = LatLng(path[i - 1].lat + (b.lat - path[i - 1].lat) * k, path[i - 1].lng + (b.lng - path[i - 1].lng) * k) }
        val pa = at(a.lat, a.lng) ?: continue; val pb = at(b.lat, b.lng) ?: continue
        val alpha = if (fade) (1 - (to - min(to, (max(from, ta) + min(to, tb)) / 2)) / span).toFloat().coerceIn(0f, 1f) else 1f
        drawLine(color.copy(alpha = color.alpha * alpha), pa, pb, width, cap = if (dashed == null) StrokeCap.Round else StrokeCap.Butt, pathEffect = dashed)
    }
}

/** The web's position markers, a 32-unit box each: a plane, a car, a tram, a walker, a dot, and fast-forward chevrons. */
private class StampIcons {
    private val paths = mapOf(
        Stamp.FLY to "M16 2c1.2 0 2 1 2 2.6V12l11 6.5v3l-11-3.4v6.2l3 2.2V29l-5-1.5L11 29v-2.5l3-2.2v-6.2L3 21.5v-3L14 12V4.6C14 3 14.8 2 16 2z",
        Stamp.DRIVE to "M9 6h14l3 8h2a2 2 0 0 1 2 2v7h-3v2a2 2 0 0 1-4 0v-2H9v2a2 2 0 0 1-4 0v-2H2v-7a2 2 0 0 1 2-2h2l3-8zm1.5 2.5L8.6 14h14.8l-1.9-5.5h-11zM7 17.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zm18 0a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z",
        Stamp.TRANSIT to "M8 4h16a3 3 0 0 1 3 3v14a3 3 0 0 1-3 3l2 3v1h-3l-2-3H11l-2 3H6v-1l2-3a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3zm0 4v6h7V8H8zm9 0v6h7V8h-7zM9.5 17a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zm13 0a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z",
        Stamp.FOOT to "M18 3a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5zm-3.5 6.5 3 .5 2 3.5 4 2v2.5l-4.5-2-1.5-2-1 5 3 3.5V30h-2.5l-3-5.5-3 5.5H9l3.5-7.5 1-6-2 1.5V22H9v-6l5.5-6.5z",
        Stamp.STILL to "M16 16m-7 0a7 7 0 1 0 14 0a7 7 0 1 0-14 0",
        Stamp.FF to "M22 16a6 6 0 1 1-12 0 6 6 0 0 1 12 0zM2 13h8v2H2zm-2 2.5h9v1.5H0zM3 18h7v2H3z",
    ).mapValues { (_, d) -> PathParser().parsePathString(d).toPath() }

    operator fun get(k: Stamp): Path = paths.getValue(k)
}
