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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.elsewhere.app.analytics.lensOf
import com.elsewhere.app.analytics.segmentAt
import com.elsewhere.app.analytics.whereaboutsAt
import com.elsewhere.app.data.Record
import com.elsewhere.app.map.Arrival
import com.elsewhere.app.map.Arrivals
import com.elsewhere.app.map.BIG_KM
import com.elsewhere.app.map.Bounds
import com.elsewhere.app.map.Cam
import com.elsewhere.app.map.Eased
import com.elsewhere.app.map.Follow
import com.elsewhere.app.map.HEAD_REAL_S
import com.elsewhere.app.map.MapEvent
import com.elsewhere.app.map.MapHost
import com.elsewhere.app.map.PlayState
import com.elsewhere.app.map.Projector
import com.elsewhere.app.map.ReplayTrails
import com.elsewhere.app.map.RevealDots
import com.elsewhere.app.map.Stamp
import com.elsewhere.app.map.StampHold
import com.elsewhere.app.map.Trail
import com.elsewhere.app.map.Trails
import com.elsewhere.app.map.activeTrails
import com.elsewhere.app.map.intersects
import com.elsewhere.app.map.rememberMapHost
import com.elsewhere.app.map.stampOf
import com.elsewhere.app.map.trailHead
import com.elsewhere.app.map.wakeLength
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Port of web/src/map/MapScreen.tsx in Play mode over MapView.tsx's replay pass: the record runs along its trails.

/** What the chrome can do; the tab binds these to the view model, the snapshot to no-ops. */
data class ReplayActions(
    val play: () -> Unit = {}, val speed: (Double) -> Unit = {}, val scrub: (Double) -> Unit = {},
    val follow: () -> Unit = {}, val lens: (Lens) -> Unit = {},
)

@Composable
fun ReplayTab(vm: PlaybackViewModel = hiltViewModel()) {
    val data by vm.data.collectAsStateWithLifecycle()
    val trails by vm.trails.collectAsStateWithLifecycle()
    // The chrome sees the clock a few times a second; the canvas reads vm.state itself every frame.
    val state by vm.chrome.collectAsStateWithLifecycle()
    val basemap by vm.basemap.collectAsStateWithLifecycle()
    val copyright by vm.copyright.collectAsStateWithLifecycle()
    val tilesError by vm.tilesError.collectAsStateWithLifecycle()
    val rec = data
    if (rec == null) { Box(Modifier.fillMaxSize().background(Poster.Stock)); return }
    val snackbar = remember { SnackbarHostState() }
    val host = rememberMapHost(basemap)
    LaunchedEffect(tilesError) { tilesError?.let { snackbar.showSnackbar("Google refused the map tiles — using the free map. $it") } }
    LaunchedEffect(host) { host.viewport.collect { it?.let(vm::idle) } }

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
        var last = 0L; var lastHead = Double.NaN; var lastPlaying = false; var lastLens: Lens? = null; var animUntil = 0L; var lastCam: Cam? = null
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
            val newTags = arrivals.prune(tMs) || arrivals.tags !== tags
            if (newTags) tags = arrivals.tags
            // The canvas redraws per frame only while the clock runs or something eased is still settling; a paused
            // map redraws on camera and state changes alone. `frame` is the one state the canvas reads per frame.
            val changed = st.playhead != lastHead || st.playing != lastPlaying || st.lens != lastLens
            if (changed) { animUntil = tMs + SETTLE_MS; lastHead = st.playhead; lastPlaying = st.playing; lastLens = st.lens }
            val cam = host.camera.value
            val moved = cam != lastCam; lastCam = cam
            if (st.playing || tMs < animUntil || newTags || moved) frame.longValue = tMs
        }
    }

    val acts = ReplayActions(
        play = { vm.setPlaying(!vm.state.value.playing) }, speed = vm::setSpeed, scrub = vm::scrub, follow = { vm.setFollow(true) }, lens = vm::setLens,
    )
    ReplayScreen(rec, state, tags, acts, snackbar = snackbar) { m ->
        host.Content(m, basemap, copyright) {
            val tr = trails
            if (tr != null) ReplayCanvas(host, rec, tr, vm.state, frame, Modifier.fillMaxSize().onSizeChanged { viewport = it })
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

/**
 * One Canvas over whichever host is up, redrawn every frame of the clock: the paused record as hairlines, a dotted
 * wake and a solid head behind the stamp, the places reached so far, and the stamp itself in the ink of the current
 * movement. Points are placed by a pure Mercator projection of the camera the loop just set, so a frame costs no
 * SDK calls and the overlay never lags the basemap by a frame.
 */
@Composable
private fun ReplayCanvas(host: MapHost, data: Record, trails: ReplayTrails, state: StateFlow<PlayState>, frame: State<Long>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val icons = remember { StampIcons() }
    val dots = remember(data) { RevealDots(data) }
    val wake = remember { Eased(2000) }
    val ring = remember { Eased(180, 500) }
    val stamp = remember { StampHold() }
    val hairlines = remember { Hairlines() }
    val bands = remember { Array(FADE_BANDS) { Path() } }
    val texts = remember { HashMap<String, TextLayoutResult>() }
    val taken = remember { ArrayList<FloatArray>() }
    val labelStyle = PosterType.Label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Poster.Ink, letterSpacing = 0.02.em)
    Canvas(modifier) {
        val tMs = frame.value
        val st = state.value
        if (st.playhead.isNaN()) return@Canvas
        val cam = host.camera.value
        val now = st.playhead; val nowL = now.toLong(); val rate = st.rate; val d = density
        val lensTrails = trails.simplified(st.lens, cam.zoom.toDouble())
        val proj = Projector(cam, size.width.roundToInt(), size.height.roundToInt(), d)

        if (!st.playing) {
            // Paused: the whole record as hairlines, like the strip printed on the pass; projected once per camera.
            for ((path, color) in hairlines.of(cam, st.lens, size, lensTrails, proj)) drawPath(path, color, style = Stroke(1.5f * d))
        }
        // A dotted wake that fades over ~16 s of screen time, then a solid head in the leg's own ink over only the live trails.
        val headLen = HEAD_REAL_S * rate; val wakeLen = wake.at(wakeLength(rate), tMs)
        val dash = PathEffect.dashPathEffect(floatArrayOf(5 * d, 4 * d))
        for (t in activeTrails(lensTrails, now - wakeLen, now)) {
            val c = ink(t.lens).copy(alpha = (if (st.playing) 0.8f else 0.4f) * if (t.guessed) 110 / 255f else 1f)
            polyline(t, now - wakeLen, now, c, 2.5f * d, fade = true, dashed = dash, proj, bands)
        }
        for (t in activeTrails(lensTrails, now - headLen * 1.6, now)) {
            val c = ink(t.lens).copy(alpha = if (t.guessed) 110 / 255f else 1f)
            polyline(t, now - headLen, now, c, 6f * d, fade = true, dashed = null, proj, bands)
        }

        // Only places already visited exist on the map; a fresh one fades in over a second of screen time.
        val fadeSpan = max(60.0, rate * 1.2)
        val pxPerM = 256 * d * 2.0.pow(cam.zoom.toDouble()) / (40_075_016.686 * cos(cam.lat * PI / 180))
        taken.clear()
        val labels = cam.zoom >= 6f
        for (dot in dots.reachedBy(nowL)) {
            val p = dot.place
            val o = proj.at(p.lat, p.lng)
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

        // The stamp: a stock disc with the leg's ink ring and the vehicle inside, always upright like the web's.
        val w = whereaboutsAt(data, nowL) ?: return@Canvas
        val still = w.mode == "STILL"
        val head = if (still) null else trailHead(lensTrails, now)
        val lat = head?.lat ?: w.lat; val lng = head?.lng ?: w.lng
        val seg = if (still) null else segmentAt(data, nowL)
        val kind = stamp.kind(if (still) Stamp.STILL else stampOf(lensOf(w.mode)), rate, seg != null && seg.kind == SegmentKind.ACTIVITY && seg.km >= BIG_KM, tMs)
        val c = when (kind) { Stamp.STILL, Stamp.FF -> Poster.Ink; Stamp.FLY -> ink(Lens.FLY); Stamp.DRIVE -> ink(Lens.DRIVE); Stamp.TRANSIT -> ink(Lens.TRANSIT); Stamp.FOOT -> ink(Lens.FOOT) }
        val r = (ring.at(if (kind == Stamp.STILL) 11.0 else 20.0, tMs) * d).toFloat()
        val o = proj.at(lat, lng)
        drawCircle(Poster.Stock, r, o); drawCircle(c, r, o, style = Stroke(3 * d))
        val sz = r * 1.3f
        translate(o.x - sz / 2, o.y - sz / 2) {
            scale(sz / 32f, pivot = Offset.Zero) { drawPath(icons[kind], c) }
        }
    }
}

/** How long after the last change the canvas keeps redrawing, so the eased wake, ring and stamp hold can settle. */
private const val SETTLE_MS = 2500L

/** Alpha steps a fading trail is drawn in: one stroked path per band instead of a line per segment. */
private const val FADE_BANDS = 4

/**
 * The paused record's hairlines as screen paths, one per ink, kept until the camera, lens or viewport changes: on a
 * ten-year record the projection of every point each frame is what made a still map expensive. Legs whose box
 * misses the view are skipped whole, and off-screen runs within a leg are not projected either.
 */
private class Hairlines {
    private var key: Any? = null
    private var paths: List<Pair<Path, Color>> = emptyList()

    fun of(cam: Cam, lens: Lens, size: Size, trails: Trails, proj: Projector): List<Pair<Path, Color>> {
        val k = listOf(cam, lens, size, trails)
        if (k == key) return paths
        val view = proj.bounds
        val padLat = (view.neLat - view.swLat) * 0.1; val padLng = (view.neLng - view.swLng) * 0.1
        val pad = Bounds(view.swLat - padLat, view.swLng - padLng, view.neLat + padLat, view.neLng + padLng)
        val byInk = LinkedHashMap<Color, Path>()
        for (t in trails) {
            if (!pad.intersects(t.box)) continue
            val a = if (t.lens == Lens.FLY) 140 / 255f else if (t.guessed) 50 / 255f else 90 / 255f
            val path = byInk.getOrPut(ink(t.lens).copy(alpha = a)) { Path() }
            var pen = false
            val pts = t.path
            for (i in pts.indices) {
                val p = pts[i]
                val near = pad.has(p.lat, p.lng) || (i + 1 < pts.size && pad.has(pts[i + 1].lat, pts[i + 1].lng))
                if (!near) { pen = false; continue }
                val x = proj.x(p.lng); val y = proj.y(p.lat)
                if (pen) path.lineTo(x, y) else { path.moveTo(x, y); pen = true }
            }
        }
        key = k; paths = byInk.map { (c, p) -> p to c }
        return paths
    }
}

/**
 * A trail's points inside [from, to] as stroked paths, the ends cut to the window so the head sits exactly at now.
 * A fading trail is split by time into [FADE_BANDS] paths of rising alpha; `bands` are rewound and reused each call.
 */
private fun DrawScope.polyline(
    t: Trail, from: Double, to: Double, color: Color, width: Float, fade: Boolean, dashed: PathEffect?, proj: Projector, bands: Array<Path>,
) {
    val ts = t.ts; val pts = t.path; val n = ts.size
    if (n < 2 || ts[n - 1] < from || ts[0] > to) return
    // The first point past `from`; the segment before it is where the window opens.
    var lo = 1; var hi = n - 1
    while (lo < hi) { val m = (lo + hi) ushr 1; if (ts[m] > from) hi = m else lo = m + 1 }
    val span = max(1.0, to - from)
    val nb = if (fade) bands.size else 1
    for (b in 0 until nb) bands[b].rewind()
    var i = lo - 1
    var pT: Double; var x: Float; var y: Float
    if (ts[i] < from) {
        val k = (from - ts[i]) / max(1e-9, ts[lo] - ts[i])
        x = proj.x(pts[i].lng + (pts[lo].lng - pts[i].lng) * k); y = proj.y(pts[i].lat + (pts[lo].lat - pts[i].lat) * k); pT = from
    } else { x = proj.x(pts[i].lng); y = proj.y(pts[i].lat); pT = ts[i] }
    var band = -1
    var j = lo
    while (true) {
        val tj = ts[j]
        val nx: Float; val ny: Float; val nT: Double
        if (tj > to) {
            val k = (to - ts[j - 1]) / max(1e-9, tj - ts[j - 1])
            nx = proj.x(pts[j - 1].lng + (pts[j].lng - pts[j - 1].lng) * k); ny = proj.y(pts[j - 1].lat + (pts[j].lat - pts[j - 1].lat) * k); nT = to
        } else { nx = proj.x(pts[j].lng); ny = proj.y(pts[j].lat); nT = tj }
        val sb = if (fade) (((pT + nT) / 2 - from) / span * nb).toInt().coerceIn(0, nb - 1) else 0
        if (sb != band) { band = sb; bands[band].moveTo(x, y) }
        bands[band].lineTo(nx, ny)
        x = nx; y = ny; pT = nT
        if (tj >= to || j == n - 1) break
        j++
    }
    val cap = if (dashed == null) StrokeCap.Round else StrokeCap.Butt
    for (b in 0 until nb) {
        if (bands[b].isEmpty) continue
        val a = if (fade) (b + 0.5f) / nb else 1f
        drawPath(bands[b], color.copy(alpha = color.alpha * a), style = Stroke(width, cap = cap, pathEffect = dashed))
    }
}

/** Whether a point is inside this box, allowing the box's longitudes to run past the date line. */
private fun Bounds.has(lat: Double, lng: Double): Boolean {
    if (lat < swLat || lat > neLat) return false
    return (lng in swLng..neLng) || (lng + 360 in swLng..neLng) || (lng - 360 in swLng..neLng)
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
