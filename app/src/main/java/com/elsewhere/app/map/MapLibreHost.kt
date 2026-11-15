package com.elsewhere.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import com.elsewhere.app.data.Viewport
import com.elsewhere.app.ui.world.PosterType
import java.io.File
import com.elsewhere.app.ui.world.Poster
import dev.sargunv.maplibrecompose.compose.CameraState
import dev.sargunv.maplibrecompose.compose.ClickResult
import dev.sargunv.maplibrecompose.compose.MaplibreMap
import dev.sargunv.maplibrecompose.core.CameraMoveReason
import dev.sargunv.maplibrecompose.core.CameraPosition
import dev.sargunv.maplibrecompose.core.GestureSettings
import dev.sargunv.maplibrecompose.core.OrnamentSettings
import io.github.dellisd.spatialk.geojson.Position
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** What the base map is drawn from: the free style's URL, or Google's raster tiles with the text their terms want shown. */
sealed interface Basemap {
    data object Free : Basemap
    data class Google(val styleJson: String) : Basemap
}

/**
 * MapLibre loads styles by URI only and ignores a URI it already has, so Google's inline style goes to a file
 * named by a hash of its content: a new session or key is a new URI. Written whole, then renamed into place.
 */
fun styleFile(dir: File, styleJson: String): File {
    cachedStyleFile(dir, styleJson)?.let { return it }
    val file = styleFileFor(dir, styleJson)
    val tmp = File(dir, "${file.name}.tmp")
    tmp.writeText(styleJson)
    if (!tmp.renameTo(file)) { tmp.delete(); file.writeText(styleJson) }
    return file
}

/** The style file for this content if an earlier visit already wrote it; null means it still has to be written. */
fun cachedStyleFile(dir: File, styleJson: String): File? = styleFileFor(dir, styleJson).takeIf { it.isFile }

private fun styleFileFor(dir: File, styleJson: String): File {
    val hash = MessageDigest.getInstance("SHA-1").digest(styleJson.toByteArray()).joinToString("") { "%02x".format(it) }
    return File(dir, "google-tiles-$hash.json")
}

/**
 * The map: MapLibre drawing either OpenFreeMap's vector tiles (OpenStreetMap data, no key, no bill) or Google's
 * roadmap raster tiles through a Map Tiles session. Both providers' terms need attribution kept on screen, so the
 * ornament stays and Google's copyright line prints beside it. Zooms convert to Google's convention.
 */
class MapLibreHost(private val scope: CoroutineScope, initial: Cam, private val state: CameraState) : MapHost {
    private val _camera = MutableStateFlow(initial)
    override val camera: StateFlow<Cam> = _camera.asStateFlow()
    private val _events = MutableSharedFlow<MapEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<MapEvent> = _events.asSharedFlow()
    private val _taps = MutableSharedFlow<Tap>(extraBufferCapacity = 8)
    override val taps: SharedFlow<Tap> = _taps.asSharedFlow()
    private val _viewport = MutableStateFlow<Viewport?>(null)
    override val viewport: StateFlow<Viewport?> = _viewport.asStateFlow()

    private var density = 1f
    private val framer = Framer { cam, ms -> moveTo(cam, ms) }
    private val watch = MoveWatch()

    override fun moveTo(cam: Cam, animateMs: Int) {
        watch.wrote(cam)
        if (animateMs <= 0) { state.position = cam.toLibre(); _camera.value = cam }
        // animateTo waits for the map to exist and cancels any move still running, so it always lands.
        else scope.launch { state.animateTo(cam.toLibre(), animateMs.milliseconds) }
    }

    /** maplibre-compose 0.5.0 has no fitBounds, so the frame is solved in Mercator once the view has a size. */
    override fun fit(bounds: Bounds, paddingPx: Int) = framer.fit(bounds, paddingPx)

    /** The library answers in dp and throws before the map exists, so scale here and treat "no map yet" as null. */
    override fun project(lat: Double, lng: Double): Offset? =
        runCatching { state.screenLocationFromPosition(Position(lng, lat)) }.getOrNull()?.let { Offset(it.x.value * density, it.y.value * density) }

    private fun visible(): Viewport? = runCatching { state.queryVisibleBoundingBox() }.getOrNull()?.let {
        Viewport(_camera.value.zoom.toInt(), north = it.northeast.latitude, south = it.southwest.latitude, east = it.northeast.longitude, west = it.southwest.longitude)
    }

    @OptIn(FlowPreview::class)
    @Composable
    override fun Content(modifier: Modifier, basemap: Basemap, copyright: String?, overlays: @Composable () -> Unit) {
        density = LocalDensity.current.density
        framer.density = density
        val cacheDir = LocalContext.current.cacheDir
        // A style written on an earlier visit is used from the first frame, so a verified key never flashes the free map.
        val styleUri by produceState(
            when (basemap) { Basemap.Free -> STYLE; is Basemap.Google -> cachedStyleFile(cacheDir, basemap.styleJson)?.toURI()?.toString() ?: STYLE },
            basemap,
        ) {
            value = when (basemap) {
                Basemap.Free -> STYLE
                is Basemap.Google -> withContext(Dispatchers.IO) { styleFile(cacheDir, basemap.styleJson).toURI().toString() }
            }
        }
        LaunchedEffect(Unit) {
            // The library exposes no "is moving" flag: the watch tells a gesture's camera from our own writes, and a
            // quiet spell after a gesture ends it.
            val positions = snapshotFlow { state.position }
            launch {
                positions.collect { p ->
                    val prev = _camera.value; val cam = p.toCam()
                    _camera.value = cam
                    for (e in watch.saw(cam, prev, state.moveReason == CameraMoveReason.GESTURE)) _events.tryEmit(e)
                }
            }
            launch { positions.debounce(IDLE_MS).collect { watch.idle()?.let { _events.tryEmit(it) }; _viewport.value = visible() } }
        }

        Box(modifier.background(Poster.Stock).onSizeChanged { framer.size = it }) {
            MaplibreMap(
                modifier = Modifier.fillMaxSize(),
                styleUri = styleUri,
                pitchRange = 0f..0f,
                gestureSettings = GestureSettings(isTiltGesturesEnabled = false),
                ornamentSettings = OrnamentSettings(
                    isLogoEnabled = true, logoAlignment = Alignment.BottomStart,
                    isAttributionEnabled = true, attributionAlignment = Alignment.BottomEnd,
                    isCompassEnabled = false, isScaleBarEnabled = false,
                ),
                cameraState = state,
                onMapClick = { p, _ -> _taps.tryEmit(Tap(p.latitude, p.longitude)); ClickResult.Pass },
            )
            overlays()
            if (basemap is Basemap.Google && copyright != null) {
                Text(
                    copyright, color = Poster.Ink, maxLines = 1,
                    style = PosterType.Label.copy(fontSize = 9.sp, letterSpacing = 0.sp),
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 96.dp, bottom = 6.dp, end = 96.dp).background(Poster.Stock.copy(alpha = 0.8f)).padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }

    private companion object {
        const val STYLE = "https://tiles.openfreemap.org/styles/liberty"
        const val IDLE_MS = 150L
    }
}

internal fun Cam.toLibre() = CameraPosition(bearing = bearing.toDouble(), target = Position(lng, lat), zoom = googleToLibreZoom(zoom).toDouble())
private fun CameraPosition.toCam() = Cam(target.latitude, target.longitude, libreToGoogleZoom(zoom.toFloat()), bearing.toFloat())
