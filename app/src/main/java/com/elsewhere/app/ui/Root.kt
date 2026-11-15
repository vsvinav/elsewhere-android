package com.elsewhere.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.ui.onboard.OnboardingGraph
import com.elsewhere.app.ui.onboard.Step
import com.elsewhere.app.ui.world.figures.LocalLand

/** The front door until the record is theirs; the app after. Menu items re-open the door at a step. */
@Composable
fun Root(vm: RootViewModel = hiltViewModel()) {
    val hasImport by vm.hasImport.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val land by vm.land.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<Step?>(null) }
    val needsDoor = !hasImport || profile == null || profile!!.homes.isEmpty()
    CompositionLocalProvider(LocalLand provides land) {
        when {
            needsDoor -> OnboardingGraph(startStep = Step.Import, onClose = null)
            editing != null -> OnboardingGraph(startStep = editing!!, onClose = { editing = null })
            else -> AppScaffold(onChangeAnswers = { editing = Step.Name }, onMapsKey = { editing = Step.Key })
        }
    }
}
