package com.elsewhere.app.map

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.elsewhere.app.data.MapProvider
import com.elsewhere.app.data.Settings
import dev.sargunv.maplibrecompose.compose.rememberCameraState

/** Which map renders; mirrors web/src/map/provider.ts. */
enum class Provider { GOOGLE, OSM }

/**
 * The setting if forced, else Google when there is a working key and OpenStreetMap otherwise. A blank key is never
 * Google. `sessionDown` is this process's own "Google didn't come up" flag, which only sways the automatic choice.
 */
fun resolve(s: Settings, sessionDown: Boolean = false): Provider = when (s.mapProvider) {
    MapProvider.OSM -> Provider.OSM
    MapProvider.GOOGLE -> if (s.mapsKey.isNotBlank()) Provider.GOOGLE else Provider.OSM
    MapProvider.AUTO -> if (s.mapsKey.isNotBlank() && !s.googleRejected && !sessionDown) Provider.GOOGLE else Provider.OSM
}

/**
 * Google flips to "down" for the process when its map won't start or load, like the web's `markGoogleDown`,
 * but only for the key that failed: a new key gets its own try.
 */
object GoogleDown {
    var key by mutableStateOf<String?>(null)
        private set
    val down: Boolean get() = key != null
    fun isDown(mapsKey: String): Boolean = key == mapsKey
    fun mark(mapsKey: String) { key = mapsKey }
    fun reset() { key = null }
}

/**
 * The host for the current settings, kept across recompositions until the provider, key or map id changes.
 * When Google won't come up, the host marks [GoogleDown] (so an automatic choice swaps to OSM for this session)
 * and `onGoogleFailed` fires once; the caller counts failures and persists `Settings.googleRejected` on repeats.
 * A host whose [GoogleMapHost.needsRestart] is set is running on a key the SDK already read; show a restart notice.
 */
@Composable
fun rememberMapHost(settings: Settings, initial: Cam = Cam(20.0, 0.0, 2f), onGoogleFailed: () -> Unit, onGoogleLoaded: () -> Unit = {}): MapHost {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val failed by rememberUpdatedState(onGoogleFailed)
    val loaded by rememberUpdatedState(onGoogleLoaded)
    val provider = resolve(settings, GoogleDown.isDown(settings.mapsKey))
    // CameraState's constructor is internal to maplibre-compose, so the library remembers it for us.
    val libreState = rememberCameraState(initial.toLibre())
    return remember(provider, settings.mapsKey, settings.mapId) {
        when (provider) {
            Provider.GOOGLE -> {
                val live = KeyInstaller.install(context, settings.mapsKey)
                // Counted once per process: a forced-Google user rebuilding the host per visit must not fail twice in one session.
                GoogleMapHost(
                    scope, initial, settings.mapId, keyLive = live,
                    onFailed = { if (!GoogleDown.down) { GoogleDown.mark(settings.mapsKey); failed() } }, onLoaded = { loaded() },
                )
            }
            Provider.OSM -> MapLibreHost(scope, initial, libreState)
        }
    }
}

/**
 * The Maps SDK reads its key from manifest meta-data only, and ours is typed in at onboarding, so the key is
 * written into the live `ApplicationInfo` before the SDK first initialises. Once the SDK has read it ([markStarted])
 * a changed key only takes effect after a process restart, which is what the web's `location.reload()` does too;
 * [install] then answers false so nobody judges the new key by the old one's tiles.
 */
object KeyInstaller {
    private const val META = "com.google.android.geo.API_KEY"
    private const val TAG = "KeyInstaller"
    @Volatile private var installed: String? = null
    @Volatile private var live: String? = null

    /** Puts `key` where the SDK will find it; true when the SDK is (or will be) running on this very key. */
    fun install(context: Context, key: String): Boolean = install(key) { k ->
        // shortcut: relies on PackageManager handing back its cached ApplicationInfo (API 30+); the Log.w below says when it didn't.
        val pm = context.packageManager
        pm.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData?.putString(META, k)
        val back = pm.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData?.getString(META)
        if (back != k) Log.w(TAG, "ApplicationInfo was not cached; the Maps SDK will read the manifest key instead")
    }

    internal fun install(key: String, write: (String) -> Unit): Boolean {
        live?.let { return it == key }
        if (installed != key) { write(key); installed = key }
        return true
    }

    /** Call once the SDK has initialised: whatever was installed is now the key it holds for the process. */
    fun markStarted() { if (live == null) live = installed }

    internal fun reset() { installed = null; live = null }
}
