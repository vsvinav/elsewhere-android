package com.elsewhere.app.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.ui.onboard.OnboardFrame
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType

/** The door's first page: the permission checklist, then Start recording; `onStart` is taken once the recorder is on. */
@Composable
fun RecordStepRoute(onStart: () -> Unit, onImport: () -> Unit, vm: RecordViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    RecordStepScreen(ui, recordActions(vm, onStart = onStart), onImport)
}

@Composable
fun RecordStepScreen(ui: RecordUi, actions: RecordActions, onImport: () -> Unit) {
    val fine = ui.rows.firstOrNull { it.perm == Perm.FINE }?.state == PermState.GRANTED
    OnboardFrame(question = null, onCancel = null) {
        DisplayText("Elsewhere records as you live.", 56.sp)
        Spacer(Modifier.height(24.dp))
        Text("Keep the phone in your pocket. Stops, nights away and trips appear on their own.", color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 17.sp))
        Spacer(Modifier.height(32.dp))
        PermissionRows(ui, actions, battery = !ui.batteryOk)
        Spacer(Modifier.height(40.dp))
        InkButton("Start recording", actions.onStart, Modifier.fillMaxWidth(), enabled = fine)
        Spacer(Modifier.height(24.dp))
        CapsText("I have a Google Timeline export instead →", Modifier.clickable(onClick = onImport), color = Poster.Ink2, size = 12.sp)
    }
}
