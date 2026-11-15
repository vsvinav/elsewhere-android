package com.elsewhere.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.ui.onboard.OnboardingGraph
import com.elsewhere.app.ui.onboard.Step
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.figures.LocalLand

/**
 * The front door until the record is theirs; the app after. Menu items re-open the door at a step.
 * Blank stock until the stores have been read, so a cold start never flashes the wrong door.
 * `door` and `app` are slots so a test can hand in view models.
 */
@Composable
fun Root(
    vm: RootViewModel = hiltViewModel(),
    door: @Composable (startStep: Step, onClose: (() -> Unit)?) -> Unit = { s, close -> OnboardingGraph(s, close) },
    app: @Composable (onChangeAnswers: () -> Unit, onMapsKey: () -> Unit) -> Unit = { a, k -> AppScaffold(a, k) },
) {
    val ready by vm.ready.collectAsStateWithLifecycle()
    val hasImport by vm.hasImport.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val land by vm.land.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<Step?>(null) }
    val needsDoor = !hasImport || profile == null || profile!!.homes.isEmpty()
    CompositionLocalProvider(LocalLand provides land) {
        when {
            !ready -> Box(Modifier.fillMaxSize().background(Poster.Stock))
            needsDoor -> door(Step.Import, null)
            editing != null -> door(editing!!) { editing = null }
            else -> app({ editing = Step.Name }, { editing = Step.Key })
        }
    }
}
