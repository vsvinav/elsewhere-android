package com.elsewhere.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.elsewhere.app.R
import com.elsewhere.app.data.Viewport
import com.elsewhere.app.ui.world.Poster
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
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

/**
 * The map on a build that carries a Maps SDK key: Google's native map, the same basemap the web's Maps JS draws,
 * free of charge, styled by the user's Map ID or else by the poster style in res/raw. The SDK prints its own logo and attribution, so `copyright`
 * goes unused here. Zooms are already Google's convention, so nothing converts.
 */
class GoogleMapHost(private val scope: CoroutineScope, initial: Cam, private val state: CameraPositionState = CameraPositionState(initial.toGoogle())) : MapHost {
    private val _camera = MutableStateFlow(initial)
    override val camera: StateFlow<Cam> = _camera.asStateFlow()
    private val _events = MutableSharedFlow<MapEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<MapEvent> = _events.asSharedFlow()
    private val _taps = MutableSharedFlow<Tap>(extraBufferCapacity = 8)
    override val taps: SharedFlow<Tap> = _taps.asSharedFlow()
    private val _viewport = MutableStateFlow<Viewport?>(null)
    override val viewport: StateFlow<Viewport?> = _viewport.asStateFlow()

    // The SDK frames bounds itself: it knows the zoom floor this screen allows, where a hand-solved zoom below it
    // would be clamped and leave the outer pins off the edge.
    private val framer = Framer { b, size, pad ->
        val ll = LatLngBounds(LatLng(b.swLat, b.swLng), LatLng(b.neLat, b.neLng))
        animate(CameraUpdateFactory.newLatLngBounds(ll, size.width, size.height, pad), Framer.ANIMATE_MS)
    }
    private val watch = MoveWatch()

    override fun moveTo(cam: Cam, animateMs: Int) {
        // The follow-cam reports a still camera twice a second; the map is already there, so nothing is written.
        if (animateMs <= 0 && cam.near(_camera.value)) return
        watch.wrote(cam)
        val update = CameraUpdateFactory.newCameraPosition(cam.toGoogle())
        if (animateMs <= 0) { state.move(update); _camera.value = cam }
        else animate(update, animateMs)
    }

    /** animate suspends until the map exists and cancels any move still running, so it always lands. */
    private fun animate(update: CameraUpdate, ms: Int) { scope.launch { state.animate(update, ms) } }

    override fun fit(bounds: Bounds, paddingPx: Int) = framer.fit(bounds, paddingPx)

    /** The SDK answers in px already; null until the map has a projection. */
    override fun project(lat: Double, lng: Double): Offset? =
        state.projection?.toScreenLocation(LatLng(lat, lng))?.let { Offset(it.x.toFloat(), it.y.toFloat()) }

    private fun visible(): Viewport? = state.projection?.visibleRegion?.latLngBounds?.let {
        Viewport(_camera.value.zoom.toInt(), north = it.northeast.latitude, south = it.southwest.latitude, east = it.northeast.longitude, west = it.southwest.longitude)
    }

    @OptIn(FlowPreview::class)
    @Composable
    override fun Content(modifier: Modifier, basemap: Basemap, copyright: String?, overlays: @Composable () -> Unit) {
        framer.density = LocalDensity.current.density
        val mapId = (basemap as? Basemap.Sdk)?.mapId.orEmpty()
        val context = LocalContext.current
        val style = remember(context) { MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style) }
        LaunchedEffect(Unit) {
            // The same watch as MapLibre: the SDK blames a gesture per move, and a quiet spell after one ends it.
            val positions = snapshotFlow { state.position }
            launch {
                positions.collect { p ->
                    val prev = _camera.value; val cam = p.toCam()
                    _camera.value = cam
                    for (e in watch.saw(cam, prev, state.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE)) _events.tryEmit(e)
                }
            }
            launch { positions.debounce(IDLE_MS).collect { watch.idle()?.let { _events.tryEmit(it) }; _viewport.value = visible() } }
        }

        Box(modifier.background(Poster.Stock).onSizeChanged { framer.size = it }) {
            // A Map ID is fixed when the view is made, so a changed one remakes the map; the camera state carries over.
            key(mapId) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = state,
                    googleMapOptionsFactory = { GoogleMapOptions().also { if (mapId.isNotBlank()) it.mapId(mapId) } },
                    properties = remember(mapId, style) { mapProperties(mapId, style) },
                    uiSettings = UI,
                    onMapClick = { _taps.tryEmit(Tap(it.latitude, it.longitude)) },
                )
            }
            overlays()
        }
    }

    private companion object {
        const val IDLE_MS = 150L
        val UI = MapUiSettings(
            compassEnabled = false, indoorLevelPickerEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false,
            rotationGesturesEnabled = false, tiltGesturesEnabled = false, zoomControlsEnabled = false,
        )
    }
}

/**
 * The web's choice in style.ts: a Map ID means Cloud styling and the JSON must stay out of its way; without one the
 * poster style paints the basemap. Buildings, indoor plans and traffic are never drawn, whichever way.
 */
internal fun mapProperties(mapId: String, style: MapStyleOptions?): MapProperties = MapProperties(
    isBuildingEnabled = false, isIndoorEnabled = false, isTrafficEnabled = false, mapType = MapType.NORMAL,
    mapStyleOptions = if (mapId.isBlank()) style else null,
)

internal fun Cam.toGoogle() = CameraPosition(LatLng(lat, lng), zoom, 0f, bearing)
internal fun CameraPosition.toCam() = Cam(target.latitude, target.longitude, zoom, bearing)
