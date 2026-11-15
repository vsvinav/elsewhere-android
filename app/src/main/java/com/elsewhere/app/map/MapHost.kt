package com.elsewhere.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

/** Where the camera sits. `zoom` is in Google's 256px-tile convention on every provider; mirrors web/src/map/cam.ts. */
data class Cam(val lat: Double, val lng: Double, val zoom: Float, val bearing: Float = 0f)

/** A box on the globe, south-west to north-east; our own type so neither SDK's LatLngBounds leaks past the hosts. */
data class Bounds(val swLat: Double, val swLng: Double, val neLat: Double, val neLng: Double)

/** What the follow-cam and deck listen for: `Move` fires through every frame of a pan or zoom, `Idle` once it settles. */
sealed interface MapEvent {
    data object Move : MapEvent
    data object Zoom : MapEvent
    data object DragStart : MapEvent
    data object Idle : MapEvent
}

/**
 * Tells a hand on the map from the camera the app writes itself. A follow-cam writes every frame, so neither
 * "the camera changed" nor "the map went quiet" can mean a drag on its own: a drag is a gesture-reasoned change that
 * is not what we last wrote, reported once until a programmatic move or an idle spell ends it.
 */
class MoveWatch {
    private var written: Cam? = null
    private var settled = true
    private var inGesture = false

    fun wrote(cam: Cam) { written = cam }

    /** Events for a camera the SDK reports, given what it showed before and whether the SDK blames a gesture. */
    fun saw(cam: Cam, prev: Cam, gesture: Boolean): List<MapEvent> {
        if (cam == prev) return emptyList()
        val out = ArrayList<MapEvent>(3)
        val ours = written?.let { cam.near(it) } == true
        if (ours) written = null
        if (ours || !gesture) inGesture = false
        else {
            settled = false
            if (!inGesture) { inGesture = true; out.add(MapEvent.DragStart) }
        }
        out.add(MapEvent.Move)
        if (cam.zoom != prev.zoom) out.add(MapEvent.Zoom)
        return out
    }

    /** The quiet spell: Idle once per unsettled stretch. */
    fun idle(): MapEvent? {
        if (settled) return null
        settled = true; inGesture = false
        return MapEvent.Idle
    }
}

private fun Cam.near(o: Cam) = abs(lat - o.lat) < 1e-7 && abs(lng - o.lng) < 1e-7 && abs(zoom - o.zoom) < 1e-3 && abs(bearing - o.bearing) < 1e-3

/** A tap on the bare map, in degrees; overlays decide what it hit. */
data class Tap(val lat: Double, val lng: Double)

/** The few camera calls the map screens need, so they run on Google or MapLibre alike. */
interface MapHost {
    val camera: StateFlow<Cam>
    val events: SharedFlow<MapEvent>
    val taps: SharedFlow<Tap>
    fun moveTo(cam: Cam, animateMs: Int = 0)
    fun fit(bounds: Bounds, paddingPx: Int)
    /** Where a point lands on screen, in px from [Content]'s top-left; null before the map has laid out. */
    fun project(lat: Double, lng: Double): Offset?
    @Composable fun Content(modifier: Modifier, overlays: @Composable () -> Unit)
}

// MapLibre's zoom 0 is a 512px world; Google's is 256px, so the same view is one level apart.
fun googleToLibreZoom(zoom: Float): Float = zoom - 1f
fun libreToGoogleZoom(zoom: Float): Float = zoom + 1f

private const val TILE = 256.0
private const val MAX_LAT = 85.05112878

private fun mercY(lat: Double): Double {
    val l = lat.coerceIn(-MAX_LAT, MAX_LAT) * PI / 180
    return ln(tan(PI / 4 + l / 2))
}

private fun unmercY(y: Double): Double = (2 * atan(exp(y)) - PI / 2) * 180 / PI

/** The box a camera shows in a viewport of `widthPx`×`heightPx`; the inverse of [camForBounds]. Longitudes may run past ±180 near the date line. */
fun viewBounds(cam: Cam, widthPx: Int, heightPx: Int): Bounds {
    val world = TILE * 2.0.pow(cam.zoom.toDouble())
    val dLng = widthPx / world * 360.0
    val dy = heightPx / world * 2 * PI
    val cy = mercY(cam.lat)
    return Bounds(unmercY(cy - dy / 2), cam.lng - dLng / 2, unmercY(cy + dy / 2), cam.lng + dLng / 2)
}

/** Whether a normalised box `o` touches this one, allowing this box's longitudes to run past the date line. */
fun Bounds.intersects(o: Bounds): Boolean {
    if (o.neLat < swLat || o.swLat > neLat) return false
    if (neLng - swLng >= 360.0) return true
    for (shift in doubleArrayOf(0.0, 360.0, -360.0)) if (o.neLng + shift >= swLng && o.swLng + shift <= neLng) return true
    return false
}

/**
 * The camera that frames `b` inside a viewport of `widthPx`×`heightPx` with `paddingPx` kept clear on each side.
 * Mercator: a degree of longitude is a fixed pixel width; latitude is projected before taking the middle. Google zoom.
 */
fun camForBounds(b: Bounds, widthPx: Int, heightPx: Int, paddingPx: Int, maxZoom: Float = 21f): Cam {
    val w = max(1.0, widthPx - 2.0 * paddingPx); val h = max(1.0, heightPx - 2.0 * paddingPx)
    var dLng = b.neLng - b.swLng
    if (dLng < 0) dLng += 360.0
    val y0 = mercY(b.swLat); val y1 = mercY(b.neLat)
    val fracX = max(1e-12, dLng / 360.0)
    val fracY = max(1e-12, (y1 - y0) / (2 * PI))
    val zoom = min(maxZoom.toDouble(), min(log2(w / (TILE * fracX)), log2(h / (TILE * fracY))))
    var lng = b.swLng + dLng / 2
    if (lng > 180) lng -= 360.0
    return Cam(lat = unmercY((y0 + y1) / 2), lng = lng, zoom = zoom.toFloat())
}
