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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
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

/**
 * The free map: OpenFreeMap's vector tiles (OpenStreetMap data) through MapLibre, no key, no bill.
 * Their terms need the attribution kept on screen, so the ornament stays. Zooms convert to Google's convention.
 */
class MapLibreHost(private val scope: CoroutineScope, initial: Cam, private val state: CameraState) : MapHost {
    private val _camera = MutableStateFlow(initial)
    override val camera: StateFlow<Cam> = _camera.asStateFlow()
    private val _events = MutableSharedFlow<MapEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<MapEvent> = _events.asSharedFlow()
    private val _taps = MutableSharedFlow<Tap>(extraBufferCapacity = 8)
    override val taps: SharedFlow<Tap> = _taps.asSharedFlow()

    private var sizePx = IntSize.Zero
    private var density = 1f
    private var pendingFit: (() -> Unit)? = null
    private val watch = MoveWatch()

    override fun moveTo(cam: Cam, animateMs: Int) {
        watch.wrote(cam)
        if (animateMs <= 0) { state.position = cam.toLibre(); _camera.value = cam }
        else scope.launch { state.animateTo(cam.toLibre(), animateMs.milliseconds) }
    }

    /** maplibre-compose 0.5.0 has no fitBounds, so the frame is solved in Mercator once the view has a size. */
    override fun fit(bounds: Bounds, paddingPx: Int) {
        val go = { moveTo(camForBounds(bounds, sizePx.width, sizePx.height, paddingPx)) }
        if (sizePx != IntSize.Zero) go() else pendingFit = go
    }

    /** The library answers in dp and throws before the map exists, so scale here and treat "no map yet" as null. */
    override fun project(lat: Double, lng: Double): Offset? =
        runCatching { state.screenLocationFromPosition(Position(lng, lat)) }.getOrNull()?.let { Offset(it.x.value * density, it.y.value * density) }

    @OptIn(FlowPreview::class)
    @Composable
    override fun Content(modifier: Modifier, overlays: @Composable () -> Unit) {
        density = LocalDensity.current.density
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
            launch { positions.debounce(IDLE_MS).collect { watch.idle()?.let { _events.tryEmit(it) } } }
        }

        Box(modifier.background(Poster.Stock).onSizeChanged { sizePx = it; pendingFit?.invoke(); pendingFit = null }) {
            MaplibreMap(
                modifier = Modifier.fillMaxSize(),
                styleUri = STYLE,
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
        }
    }

    private companion object {
        const val STYLE = "https://tiles.openfreemap.org/styles/liberty"
        const val IDLE_MS = 150L
    }
}

internal fun Cam.toLibre() = CameraPosition(bearing = bearing.toDouble(), target = Position(lng, lat), zoom = googleToLibreZoom(zoom).toDouble())
private fun CameraPosition.toCam() = Cam(target.latitude, target.longitude, libreToGoogleZoom(zoom.toFloat()), bearing.toFloat())
