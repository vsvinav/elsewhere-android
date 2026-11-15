package com.elsewhere.app.ui.gate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.data.Ready
import com.elsewhere.app.ui.onboard.Checking
import com.elsewhere.app.ui.onboard.KeyActions
import com.elsewhere.app.ui.onboard.KeyPhase
import com.elsewhere.app.ui.onboard.OnboardFrame
import com.elsewhere.app.ui.onboard.OnboardState
import com.elsewhere.app.ui.onboard.Result
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.ReadyBar

/** The whole screen while a saved key is checked against Google, before any record is printed on it (web KeyGate). */
@Composable
fun KeyGate(vm: KeyGateViewModel = hiltViewModel(), ready: Ready? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    KeyGateScreen(state, vm, ready)
}

@Composable
fun KeyGateScreen(state: OnboardState, actions: KeyActions, ready: Ready? = null) {
    var k by rememberSaveable(state.key) { mutableStateOf(state.key) }
    Box {
        OnboardFrame(question = null, onCancel = null) {
            DisplayText("Else", 96.sp, misreg = true)
            DisplayText("where", 96.sp, color = Poster.Red, misreg = true)
            Spacer(Modifier.height(20.dp))
            Text("Checking your Google key before we print your record.", color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 18.sp))
            Spacer(Modifier.height(40.dp))
            when (state.keyPhase) {
                KeyPhase.Result -> Result(state.keyReport, k, state.mapId, { k = it }, state.limitedAck, actions)
                else -> Checking()
            }
        }
        ReadyBar(ready, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars))
    }
}
