package com.elsewhere.app.ui.tabs.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.Settings
import com.elsewhere.app.map.GoogleMapHost
import com.elsewhere.app.map.Provider
import com.elsewhere.app.map.rememberMapHost
import com.elsewhere.app.map.resolve
import com.elsewhere.app.ui.world.Poster
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The map settings a host needs, and the one write a host makes: counting sessions where Google failed to load. */
@HiltViewModel
class MapHostViewModel @Inject constructor(private val maps: MapsSettings) : ViewModel() {
    val settings: StateFlow<Settings?> = maps.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** One failed session could be the network; two on the same key and the key is taken as refused. */
    fun googleFailed() {
        viewModelScope.launch {
            val s = maps.settings.first(); val n = s.googleFailures + 1
            maps.saveSettings(s.copy(googleFailures = n, googleRejected = s.googleRejected || n >= REJECT_AFTER))
        }
    }

    /** A session that loads wipes the slate, so "two failures" means two in a row, not two ever. */
    fun googleLoaded() {
        viewModelScope.launch { val s = maps.settings.first(); if (s.googleFailures != 0) maps.saveSettings(s.copy(googleFailures = 0)) }
    }

    private companion object { const val REJECT_AFTER = 2 }
}

/** A bare host filling the tab until the Map screen lands; the fallback notice lives here for now. */
@Composable
fun MapHostDemo(vm: MapHostViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val host = rememberMapHost(s, onGoogleLoaded = vm::googleLoaded, onGoogleFailed = {
        vm.googleFailed()
        // Only an automatic choice actually swaps to OSM; a forced Google stays Google, blank or not.
        val swapped = resolve(s, sessionDown = true) == Provider.OSM
        scope.launch { snackbar.showSnackbar(if (swapped) "Google refused the key — using OpenStreetMap" else "Google Maps didn't load") }
    })
    Box(Modifier.fillMaxSize()) {
        host.Content(Modifier.fillMaxSize()) {}
        if ((host as? GoogleMapHost)?.needsRestart == true) {
            Text(
                "Restart the app to apply the new key", color = Poster.Ink,
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).background(Poster.Mustard).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
