package com.elsewhere.app.ui.onboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.data.RecordStore
import com.elsewhere.app.record.RecordControl
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject
import java.time.LocalDate
import java.time.ZoneId

data class WaitingState(val days: Int = 0, val recording: Boolean = false, val fixesToday: Int = 0, val importing: Float? = null, val error: String? = null)

/** The week the phone records before the poster has anything to say; the record stays, an export ends the wait. */
@HiltViewModel
class WaitingViewModel @Inject constructor(private val store: RecordStore, private val control: RecordControl) : ViewModel() {
    private val fixes = MutableStateFlow(0)
    private val importing = MutableStateFlow<Float?>(null)
    private val error = MutableStateFlow<String?>(null)
    val state: StateFlow<WaitingState> = combine(store.recordedDays, control.recording, fixes, importing, error) { d, r, f, i, e ->
        WaitingState(d, r.enabled, f, i, e)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WaitingState())

    init { viewModelScope.launch { fixes.value = control.fixesSince(LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()) } }

    fun resume() { viewModelScope.launch { control.start() } }

    fun import(open: () -> InputStream, fileName: String) {
        if (importing.value != null) return
        importing.value = 0f; error.value = null
        viewModelScope.launch {
            try { store.import(open, fileName) { importing.value = it } } catch (e: Exception) { error.value = e.message ?: "Couldn't read that file" }
            importing.value = null
        }
    }
}

@Composable
fun WaitingRoute(onSettings: () -> Unit, vm: WaitingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val pick = rememberTimelinePicker { uri, name -> vm.import({ resolver.openInputStream(uri) ?: throw IllegalStateException("Couldn't open that file") }, name) }
    WaitingScreen(state, vm::resume, pick, onSettings)
}

@Composable
fun WaitingScreen(state: WaitingState, onResume: () -> Unit, onImport: () -> Unit, onSettings: () -> Unit) {
    val day = (state.days + 1).coerceAtLeast(1)
    OnboardFrame(question = null, onCancel = null) {
        DisplayText("Day $day", 96.sp, misreg = true, misregBy = 1.5.dp)
        Spacer(Modifier.height(24.dp))
        Text("Come back in a week. A record needs a few nights before it has anything to say.", color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 17.sp))
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until 7) {
                val color = when { i < state.days -> Poster.Ink; i == state.days -> Poster.Red; else -> Poster.Stock3 }
                Box(Modifier.width(34.dp).height(5.dp).background(color))
            }
        }
        Spacer(Modifier.height(32.dp))
        when {
            state.importing != null -> CapsText("Reading segments…", color = Poster.Ink)
            state.recording -> CapsText("Recording · ${state.fixesToday} fixes", color = Poster.Ink)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                CapsText("Recording is paused", color = Poster.Red)
                Spacer(Modifier.weight(1f))
                InkButton("Resume", onResume, quiet = true)
            }
        }
        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(state.error, color = Poster.Red, style = PosterType.Label)
        }
        Spacer(Modifier.height(48.dp))
        CapsText("Add a Google Timeline export", Modifier.fillMaxWidth().clickable(enabled = state.importing == null, onClick = onImport).padding(vertical = 10.dp), color = Poster.Ink)
        CapsText("Settings", Modifier.fillMaxWidth().clickable(onClick = onSettings).padding(vertical = 10.dp), color = Poster.Ink)
    }
}
