package com.elsewhere.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.elsewhere.app.data.MapProvider
import com.elsewhere.app.data.Settings
import dev.sargunv.maplibrecompose.compose.rememberCameraState

/** Who draws the base map: Google's native SDK, Google's raster tiles, or the free style; mirrors web/src/map/provider.ts. */
enum class Provider { SDK, GOOGLE, FREE }

/**
 * The setting if forced, else the native SDK when this build carries its key (free, and the same Google map the
 * web shows), else Google's tiles when the key passed the Map Tiles probe at onboarding, else free
 * OpenStreetMap-based tiles. A blank key is never Google tiles.
 */
fun resolve(s: Settings, sdk: Boolean = Sdk.available): Provider = when (s.mapProvider) {
    MapProvider.FREE -> Provider.FREE
    MapProvider.GOOGLE_TILES -> if (s.mapsKey.isNotBlank()) Provider.GOOGLE else Provider.FREE
    MapProvider.AUTO -> when {
        sdk -> Provider.SDK
        s.mapsKey.isNotBlank() && s.keyTiles -> Provider.GOOGLE
        else -> Provider.FREE
    }
}

/**
 * The host for the current base map, kept across recompositions: Google's SDK for [Basemap.Sdk], MapLibre for
 * the rest, which swap their style underneath without a new host. Should the kind change mid-visit (a forced
 * setting, say), the new host starts where the old one was looking so the view does not jump.
 */
@Composable
fun rememberMapHost(basemap: Basemap, initial: Cam = Cam(20.0, 0.0, 2f)): MapHost {
    val scope = rememberCoroutineScope()
    // CameraState's constructor is internal to maplibre-compose, so the library remembers it for us.
    val libreState = rememberCameraState(initial.toLibre())
    val previous = remember { arrayOfNulls<MapHost>(1) }
    return remember(basemap is Basemap.Sdk) {
        val cam = previous[0]?.camera?.value
        val host: MapHost = if (basemap is Basemap.Sdk) GoogleMapHost(scope, cam ?: initial) else MapLibreHost(scope, cam ?: initial, libreState)
        if (cam != null) host.moveTo(cam)
        previous[0] = host
        host
    }
}
