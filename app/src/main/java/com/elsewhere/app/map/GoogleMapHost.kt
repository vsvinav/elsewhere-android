package com.elsewhere.app.map

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.elsewhere.app.R
import com.elsewhere.app.ui.world.Poster
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Google Maps on the poster stock: the web's `MAP_STYLES` as a raw JSON style (or a cloud map id when one is set),
 * no default chrome. `onRejected` fires once when the SDK won't start or the key is refused.
 */
class GoogleMapHost(
    private val scope: CoroutineScope, initial: Cam, private val mapId: String, private val onRejected: () -> Unit,
) : MapHost {
    private val state = CameraPositionState(initial.toGoogle())
    private val _camera = MutableStateFlow(initial)
    override val camera: StateFlow<Cam> = _camera.asStateFlow()
    private val _events = MutableSharedFlow<MapEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<MapEvent> = _events.asSharedFlow()

    private var loaded by mutableStateOf(false)
    private var pendingFit: (() -> Unit)? = null
    private var rejected = false

    override fun moveTo(cam: Cam, animateMs: Int) {
        if (animateMs <= 0 || !loaded) state.position = cam.toGoogle()
        else scope.launch { state.animate(CameraUpdateFactory.newCameraPosition(cam.toGoogle()), animateMs) }
    }

    /** Bounds updates need a laid-out map; before that the call waits for `onMapLoaded`. */
    override fun fit(bounds: Bounds, paddingPx: Int) {
        val go = {
            val box = LatLngBounds(LatLng(bounds.swLat, bounds.swLng), LatLng(bounds.neLat, bounds.neLng))
            runCatching { state.move(CameraUpdateFactory.newLatLngBounds(box, paddingPx)) }
        }
        if (loaded) go() else pendingFit = { go() }
    }

    private fun reject() { if (!rejected) { rejected = true; onRejected() } }

    @Composable
    override fun Content(modifier: Modifier, overlays: @Composable () -> Unit) {
        val context = LocalContext.current
        var ready by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            // Rejected-key heuristic, all in one place. The SDK has no public auth-failure callback (it only logs
            // "Authorization failure" and paints blank tiles), so: a non-SUCCESS initialise code means no Maps at all,
            // and a map that still hasn't reported loaded REJECT_MS after the SDK came up is taken as a refused key.
            // Offline devices can't load tiles either, so the timer only counts while there is a network.
            val code = runCatching { MapsInitializer.initialize(context.applicationContext, MapsInitializer.Renderer.LATEST) {} }
                .getOrDefault(ConnectionResult.SERVICE_MISSING)
            if (code != ConnectionResult.SUCCESS) { reject(); return@LaunchedEffect }
            ready = true
            delay(REJECT_MS)
            if (!loaded && online(context)) reject()
        }

        LaunchedEffect(Unit) {
            launch {
                snapshotFlow { state.position }.drop(1).collect { p ->
                    val prev = _camera.value; val cam = p.toCam()
                    _camera.value = cam
                    _events.tryEmit(MapEvent.Move)
                    if (cam.zoom != prev.zoom) _events.tryEmit(MapEvent.Zoom)
                }
            }
            launch {
                snapshotFlow { state.isMoving }.drop(1).collect { moving ->
                    if (!moving) _events.tryEmit(MapEvent.Idle)
                    else if (state.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) _events.tryEmit(MapEvent.DragStart)
                }
            }
        }

        Box(modifier.background(Poster.Stock)) {
            if (ready) GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = state,
                googleMapOptionsFactory = {
                    GoogleMapOptions().backgroundColor(Poster.Stock.toArgb()).also { if (mapId.isNotBlank()) it.mapId(mapId) }
                },
                // A cloud map id carries its own style; the JSON only applies to the plain map.
                properties = MapProperties(mapStyleOptions = if (mapId.isBlank()) MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style) else null),
                uiSettings = MapUiSettings(
                    compassEnabled = false, indoorLevelPickerEnabled = false, mapToolbarEnabled = false,
                    myLocationButtonEnabled = false, tiltGesturesEnabled = false, zoomControlsEnabled = false,
                ),
                onMapLoaded = { loaded = true; pendingFit?.invoke(); pendingFit = null },
            )
            overlays()
        }
    }

    private fun online(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        return cm.activeNetwork != null
    }

    private companion object { const val REJECT_MS = 6_000L }
}

private fun Cam.toGoogle() = CameraPosition.Builder().target(LatLng(lat, lng)).zoom(zoom).bearing(bearing).build()
private fun CameraPosition.toCam() = Cam(target.latitude, target.longitude, zoom, bearing)
