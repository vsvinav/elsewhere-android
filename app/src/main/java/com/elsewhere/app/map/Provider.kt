package com.elsewhere.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.elsewhere.app.data.MapProvider
import com.elsewhere.app.data.Settings
import dev.sargunv.maplibrecompose.compose.rememberCameraState

/** Whose tiles the map draws; mirrors web/src/map/provider.ts. */
enum class Provider { GOOGLE, FREE }

/**
 * The setting if forced, else Google's tiles when the key passed the Map Tiles probe at onboarding and free
 * OpenStreetMap-based tiles otherwise. A blank key is never Google.
 */
fun resolve(s: Settings): Provider = when (s.mapProvider) {
    MapProvider.FREE -> Provider.FREE
    MapProvider.GOOGLE_TILES -> if (s.mapsKey.isNotBlank()) Provider.GOOGLE else Provider.FREE
    MapProvider.AUTO -> if (s.mapsKey.isNotBlank() && s.keyTiles) Provider.GOOGLE else Provider.FREE
}

/** The one MapLibre host, kept across recompositions; the base map swaps underneath it as [Basemap] changes. */
@Composable
fun rememberMapHost(initial: Cam = Cam(20.0, 0.0, 2f)): MapLibreHost {
    val scope = rememberCoroutineScope()
    // CameraState's constructor is internal to maplibre-compose, so the library remembers it for us.
    val libreState = rememberCameraState(initial.toLibre())
    return remember { MapLibreHost(scope, initial, libreState) }
}
