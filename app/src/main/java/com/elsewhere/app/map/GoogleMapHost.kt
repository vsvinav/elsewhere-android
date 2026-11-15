package com.elsewhere.app.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import kotlinx.coroutines.launch

/**
 * Google Maps on the poster stock: the web's `MAP_STYLES` as a raw JSON style (or a cloud map id when one is set),
 * no default chrome. `onFailed` fires once when the SDK won't start or the map never loads; with `keyLive` false
 * the SDK is still on an older key, so nothing is judged and [needsRestart] asks for a relaunch instead.
 */
class GoogleMapHost(
    private val scope: CoroutineScope, initial: Cam, private val mapId: String,
    private val keyLive: Boolean, private val onFailed: () -> Unit,
) : MapHost {
    val needsRestart: Boolean get() = !keyLive

    private val state = CameraPositionState(initial.toGoogle())
    private val _camera = MutableStateFlow(initial)
    override val camera: StateFlow<Cam> = _camera.asStateFlow()
    private val _events = MutableSharedFlow<MapEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<MapEvent> = _events.asSharedFlow()

    private var loaded by mutableStateOf(false)
    private var pendingFit: (() -> Unit)? = null
    private var failed = false

    override fun moveTo(cam: Cam, animateMs: Int) {
        if (animateMs <= 0 || !loaded) { state.position = cam.toGoogle(); _camera.value = cam }
        else scope.launch { state.animate(CameraUpdateFactory.newCameraPosition(cam.toGoogle()), animateMs) }
    }

    /** Bounds updates need a laid-out map; before that the call waits for `onMapLoaded`. */
    override fun fit(bounds: Bounds, paddingPx: Int) {
        val go = {
            val box = LatLngBounds(LatLng(bounds.swLat, bounds.swLng), LatLng(bounds.neLat, bounds.neLng))
            runCatching { state.move(CameraUpdateFactory.newLatLngBounds(box, paddingPx)); _camera.value = state.position.toCam() }
        }
        if (loaded) go() else pendingFit = { go() }
    }

    private fun fail() { if (!failed) { failed = true; onFailed() } }

    @Composable
    override fun Content(modifier: Modifier, overlays: @Composable () -> Unit) {
        val context = LocalContext.current
        var ready by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            // The "Google didn't come up" heuristic, all in one place. The SDK has no public auth-failure callback
            // (a refused key only logs "Authorization failure" and paints blank tiles), and a non-SUCCESS initialise
            // code (Play services missing or updating) is device state, not a key problem. So neither verdict is
            // persisted here: either one fails this session only, and the caller persists `googleRejected` once
            // sessions fail twice. That costs less code than telling the two apart and never blames a good key for
            // one bad network. Offline or captive portals load no tiles either, so the timer needs validated internet.
            // With `keyLive` false the SDK still holds an older key, so there is nothing fair to judge.
            val code = runCatching { MapsInitializer.initialize(context.applicationContext, MapsInitializer.Renderer.LATEST) {} }
                .getOrDefault(ConnectionResult.SERVICE_MISSING)
            KeyInstaller.markStarted()
            if (code != ConnectionResult.SUCCESS) { if (keyLive) fail(); return@LaunchedEffect }
            ready = true
            if (!keyLive) return@LaunchedEffect
            delay(FAIL_MS)
            if (!loaded && online(context)) fail()
        }

        LaunchedEffect(Unit) {
            launch {
                snapshotFlow { state.position }.collect { p ->
                    val prev = _camera.value; val cam = p.toCam()
                    _camera.value = cam
                    if (cam != prev) _events.tryEmit(MapEvent.Move)
                    if (cam.zoom != prev.zoom) _events.tryEmit(MapEvent.Zoom)
                }
            }
            launch {
                snapshotFlow { state.isMoving }.collect { moving ->
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
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private companion object { const val FAIL_MS = 6_000L }
}

private fun Cam.toGoogle() = CameraPosition.Builder().target(LatLng(lat, lng)).zoom(zoom).bearing(bearing).build()
private fun CameraPosition.toCam() = Cam(target.latitude, target.longitude, zoom, bearing)
