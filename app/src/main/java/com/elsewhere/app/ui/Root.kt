package com.elsewhere.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.data.Ready
import com.elsewhere.app.sonar.PendingCrew
import com.elsewhere.app.ui.crew.CrewRoute
import com.elsewhere.app.ui.gate.KeyGate
import com.elsewhere.app.ui.onboard.OnboardingGraph
import com.elsewhere.app.ui.onboard.Step
import com.elsewhere.app.ui.record.RecordRoute
import com.elsewhere.app.ui.settings.SettingsRoute
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.figures.LocalLand

/**
 * The front door until the record is theirs; the app after. Menu items re-open the door at a step or lay Settings over the app.
 * Blank stock until the stores have been read, so a cold start never flashes the wrong door.
 * `door` and `app` are slots so a test can hand in view models.
 */
@Composable
fun Root(
    vm: RootViewModel = hiltViewModel(),
    door: @Composable (startStep: Step, onClose: (() -> Unit)?, onRecord: () -> Unit) -> Unit = { s, close, r -> OnboardingGraph(s, close, onRecord = r) },
    app: @Composable (onChangeAnswers: () -> Unit, onSettings: () -> Unit, onRecord: () -> Unit, onCrew: () -> Unit) -> Unit = { a, s, r, c -> AppScaffold(a, s, r, onCrew = c) },
    gate: @Composable (ready: Ready?) -> Unit = { r -> KeyGate(ready = r) },
    recordScreen: @Composable (onClose: () -> Unit) -> Unit = { RecordRoute(it) },
    settingsScreen: @Composable (onClose: () -> Unit) -> Unit = { SettingsRoute(it) },
    crewScreen: @Composable (onClose: () -> Unit) -> Unit = { CrewRoute(it) },
) {
    val ready by vm.ready.collectAsStateWithLifecycle()
    val hasImport by vm.hasImport.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val land by vm.land.collectAsStateWithLifecycle()
    val gated by vm.gated.collectAsStateWithLifecycle()
    val readyBar by vm.readyBar.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<Step?>(null) }
    var recording by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var crew by rememberSaveable { mutableStateOf(false) }
    // A pair link opens Crew with the code already claimed; the screen takes the code itself.
    val pendingCode by PendingCrew.code.collectAsStateWithLifecycle()
    LaunchedEffect(pendingCode) { if (pendingCode != null) crew = true }
    val needsDoor = !hasImport || profile == null || profile!!.homes.isEmpty()
    CompositionLocalProvider(LocalLand provides land) {
        when {
            !ready || gated == null -> Box(Modifier.fillMaxSize().background(Poster.Stock))
            recording -> recordScreen { recording = false }
            needsDoor -> door(Step.Import, null) { recording = true }
            editing != null -> door(editing!!, { editing = null }) { recording = true }
            // The web shows KeyGate once there is a profile and the key is unproven; it lifts itself when the settings change.
            gated == true -> gate(readyBar)
            // Settings sits over the app rather than replacing it, so Back lands on the tab the visitor left.
            else -> Box {
                app({ editing = Step.Name }, { settings = true }, { recording = true }, { crew = true })
                if (settings) settingsScreen { settings = false }
                if (crew) crewScreen { crew = false }
            }
        }
    }
}
