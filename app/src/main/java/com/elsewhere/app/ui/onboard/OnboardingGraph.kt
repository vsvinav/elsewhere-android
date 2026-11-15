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
    val app = LocalContext.current.applicationContext
    // Once per visit to the door, not again on rotation.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!started) { started = true; vm.start(startStep) } }
    // The view model outlives a visit, so it may still read Done from the last one: only a Done
    // seen after a live step on this visit closes the door.
    var armed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.step) {
        if (state.step != Step.Done) armed = true
        else if (armed) onClose?.invoke()
    }
    BackHandler(enabled = state.questionIndex > 0) { vm.back() }
    BackHandler(enabled = onClose != null && state.questionIndex <= 0) { onClose?.invoke() }

    AnimatedContent(
        targetState = state.step,
        transitionSpec = { (fadeIn() + slideInVertically { it / 20 }) togetherWith fadeOut() },
        label = "onboard",
    ) { step ->
        when (step) {
            Step.Import -> ImportScreen(state.importing, state.importError, state.report) { uri, name ->
                vm.import({ app.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Couldn't open that file") }, name)
            }
            Step.Key -> KeyScreen(state, vm, onCancel = onClose)
            Step.Done -> Unit
            else -> QuestionsScreen(state, vm, onCancel = onClose)
        }
    }
}
