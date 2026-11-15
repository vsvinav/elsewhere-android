package com.elsewhere.app.ui.tabs.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.Settings
import com.elsewhere.app.map.rememberMapHost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The map settings a host needs, and the one write a host makes: flagging a refused Google key. */
@HiltViewModel
class MapHostViewModel @Inject constructor(private val maps: MapsSettings) : ViewModel() {
    val settings: StateFlow<Settings?> = maps.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun googleRejected() { viewModelScope.launch { maps.saveSettings(maps.settings.first().copy(googleRejected = true)) } }
}

/** A bare host filling the tab until the Map screen lands; the fallback notice lives here for now. */
@Composable
fun MapHostDemo(vm: MapHostViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val host = rememberMapHost(s) {
        vm.googleRejected()
        scope.launch { snackbar.showSnackbar("Google refused the key — using OpenStreetMap") }
    }
    Box(Modifier.fillMaxSize()) {
        host.Content(Modifier.fillMaxSize()) {}
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
