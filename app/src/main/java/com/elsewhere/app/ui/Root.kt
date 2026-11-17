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
import com.elsewhere.app.ui.account.AccountEntry
import com.elsewhere.app.ui.account.AccountRoute
import com.elsewhere.app.ui.account.WelcomeScreen
import com.elsewhere.app.ui.crew.CrewRoute
import com.elsewhere.app.ui.gate.KeyGate
import com.elsewhere.app.ui.onboard.OnboardingGraph
import com.elsewhere.app.ui.onboard.Step
import com.elsewhere.app.ui.onboard.WaitingRoute
import com.elsewhere.app.ui.record.RecordRoute
import com.elsewhere.app.ui.settings.SettingsRoute
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.figures.LocalLand

/**
 * The front door until the record is theirs; the app after. Menu items re-open the door at a step or lay Settings over the app.
 * Blank stock until the stores have been read, so a cold start never flashes the wrong door.
 * Before any of that a fresh phone meets Welcome: sign up, log in, or continue without an account, once.
 * `door`, `app` and `account` are slots so a test can hand in view models.
 */
@Composable
fun Root(
    vm: RootViewModel = hiltViewModel(),
    door: @Composable (startStep: Step, onClose: (() -> Unit)?) -> Unit = { s, close -> OnboardingGraph(s, close) },
    app: @Composable (onChangeAnswers: () -> Unit, onSettings: () -> Unit, onRecord: () -> Unit, onCrew: () -> Unit) -> Unit = { a, s, r, c -> AppScaffold(a, s, r, onCrew = c) },
    gate: @Composable (ready: Ready?) -> Unit = { r -> KeyGate(ready = r) },
    recordScreen: @Composable (onClose: () -> Unit) -> Unit = { RecordRoute(it) },
    settingsScreen: @Composable (onClose: () -> Unit) -> Unit = { SettingsRoute(it) },
    crewScreen: @Composable (onClose: () -> Unit) -> Unit = { CrewRoute(it) },
    welcome: @Composable (onSignUp: () -> Unit, onLogIn: () -> Unit, onSkip: () -> Unit, onGoogle: () -> Unit) -> Unit = { s, l, k, g -> WelcomeScreen(s, l, k, g) },
    account: @Composable (entry: AccountEntry, onDone: () -> Unit, onClose: () -> Unit) -> Unit = { e, d, c -> AccountRoute(e, d, c) },
    waiting: @Composable (onSettings: () -> Unit) -> Unit = { WaitingRoute(it) },
) {
    val ready by vm.ready.collectAsStateWithLifecycle()
    val hasImport by vm.hasImport.collectAsStateWithLifecycle()
    val hasExport by vm.hasExport.collectAsStateWithLifecycle()
    val recordedDays by vm.recordedDays.collectAsStateWithLifecycle()
    val recordingOn by vm.recording.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val land by vm.land.collectAsStateWithLifecycle()
    val gated by vm.gated.collectAsStateWithLifecycle()
    val readyBar by vm.readyBar.collectAsStateWithLifecycle()
    val acct by vm.account.collectAsStateWithLifecycle()
    var entry by rememberSaveable { mutableStateOf<AccountEntry?>(null) }
    var editing by rememberSaveable { mutableStateOf<Step?>(null) }
    var recording by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var crew by rememberSaveable { mutableStateOf(false) }
    // A pair link opens Crew with the code filled in; the screen takes the code itself.
    val pendingCode by PendingCrew.code.collectAsStateWithLifecycle()
    LaunchedEffect(pendingCode) { if (pendingCode != null) crew = true }
    // A phone that has just started recording has no closed stay yet; the recorder being on is enough to pass the door.
    val needsDoor = profile == null || profile!!.homes.isEmpty() || (!hasImport && !recordingOn)
    // A phone with only its own record waits a week at the door; an export has history to show at once.
    val waits = !hasExport && recordedDays < 7
    val needsWelcome = profile == null && acct?.session == null && acct?.welcomed == false
    CompositionLocalProvider(LocalLand provides land) {
        when {
            !ready || gated == null || acct == null -> Box(Modifier.fillMaxSize().background(Poster.Stock))
            entry != null -> account(entry!!, { entry = null; vm.welcome() }, { entry = null })
            needsWelcome -> welcome({ entry = AccountEntry.SignUp }, { entry = AccountEntry.LogIn }, { vm.welcome() }, { entry = AccountEntry.Google })
            recording -> recordScreen { recording = false }
            needsDoor -> door(Step.Record, null)
            editing != null -> door(editing!!) { editing = null }
            // The web shows KeyGate once there is a profile and the key is unproven; it lifts itself when the settings change.
            gated == true -> gate(readyBar)
            waits -> Box {
                waiting { settings = true }
                if (settings) settingsScreen { settings = false }
            }
            // Settings sits over the app rather than replacing it, so Back lands on the tab the visitor left.
            else -> Box {
                app({ editing = Step.Name }, { settings = true }, { recording = true }, { crew = true })
                if (settings) settingsScreen { settings = false }
                if (crew) crewScreen { crew = false }
            }
        }
    }
}
