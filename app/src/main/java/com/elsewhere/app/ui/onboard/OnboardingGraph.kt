package com.elsewhere.app.ui.onboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The front door, one page per step. `startStep` is `Import` for a new record, `Name` to change
 * answers, `Key` to change just the key; `onClose` returns to the app when editing.
 */
@Composable
fun OnboardingGraph(startStep: Step, onClose: (() -> Unit)?, vm: OnboardViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    // Once per visit to the door, not again on rotation.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!started) { started = true; vm.start(startStep) } }
    LaunchedEffect(state.step) { if (state.step == Step.Done) onClose?.invoke() }
    BackHandler(enabled = state.questionIndex > 0) { vm.back() }

    AnimatedContent(
        targetState = state.step,
        transitionSpec = { (fadeIn() + slideInVertically { it / 20 }) togetherWith fadeOut() },
        label = "onboard",
    ) { step ->
        when (step) {
            Step.Import -> ImportScreen(state.importing, state.importError, state.report) { uri, name ->
                vm.import({ ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Couldn't open that file") }, name)
            }
            Step.Key -> KeyScreen(state.key, state.mapId, onSave = vm::saveKey, onSkip = vm::skipKey, onCancel = onClose)
            Step.Done -> Unit
            else -> QuestionsScreen(state, vm, onCancel = onClose)
        }
    }
}
