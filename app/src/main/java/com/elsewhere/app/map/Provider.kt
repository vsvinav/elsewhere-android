package com.elsewhere.app.map

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.elsewhere.app.data.MapProvider
import com.elsewhere.app.data.Settings
import dev.sargunv.maplibrecompose.compose.rememberCameraState

/** Which map renders; mirrors web/src/map/provider.ts. */
enum class Provider { GOOGLE, OSM }

/** The setting if forced, else Google when there is a working key and OpenStreetMap otherwise. A blank key is never Google. */
fun resolve(s: Settings): Provider = when (s.mapProvider) {
    MapProvider.OSM -> Provider.OSM
    MapProvider.GOOGLE -> if (s.mapsKey.isNotBlank()) Provider.GOOGLE else Provider.OSM
    MapProvider.AUTO -> if (s.mapsKey.isNotBlank() && !s.googleRejected) Provider.GOOGLE else Provider.OSM
}

/**
 * The host for the current settings, kept across recompositions until the provider, key or map id changes.
 * When Google refuses the key, `onGoogleRejected` fires once; the caller flips `Settings.googleRejected`, which
 * resolves to OSM and swaps the host here. Showing the "using OpenStreetMap" notice is the caller's job.
 */
@Composable
fun rememberMapHost(settings: Settings, initial: Cam = Cam(20.0, 0.0, 2f), onGoogleRejected: () -> Unit): MapHost {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val rejected by rememberUpdatedState(onGoogleRejected)
    val provider = resolve(settings)
    // CameraState's constructor is internal to maplibre-compose, so the library remembers it for us.
    val libreState = rememberCameraState(initial.toLibre())
    return remember(provider, settings.mapsKey, settings.mapId) {
        when (provider) {
            Provider.GOOGLE -> {
                KeyInstaller.install(context, settings.mapsKey)
                GoogleMapHost(scope, initial, settings.mapId, onRejected = { rejected() })
            }
            Provider.OSM -> MapLibreHost(scope, initial, libreState)
        }
    }
}

/**
 * The Maps SDK reads its key from manifest meta-data only, and ours is typed in at onboarding, so the key is
 * written into the live `ApplicationInfo` before the SDK first initialises. Once the SDK has read it, a changed
 * key only takes effect after a process restart, which is what the web's `location.reload()` does too.
 */
object KeyInstaller {
    private const val META = "com.google.android.geo.API_KEY"
    @Volatile private var installed: String? = null

    fun install(context: Context, key: String) {
        if (key == installed) return
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        info.metaData?.putString(META, key)
        if (installed != null) Log.i("KeyInstaller", "Maps key changed; the Maps SDK picks it up on the next launch")
        installed = key
    }
}
