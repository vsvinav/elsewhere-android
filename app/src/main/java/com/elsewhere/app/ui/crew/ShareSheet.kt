package com.elsewhere.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.crew.Friend
import com.elsewhere.app.crew.ShareLength
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType

private val LENGTH_LABEL = mapOf(ShareLength.HOUR to "1 hour", ShareLength.TONIGHT to "Until tonight", ShareLength.UNTIL_STOP to "Until I stop")
private val LENGTH_VERB = mapOf(ShareLength.HOUR to "Share for 1 hour", ShareLength.TONIGHT to "Share until tonight", ShareLength.UNTIL_STOP to "Share until I stop")
const val SHARE_CAPTION = "Uses the fixes Record already collects — no extra GPS. Pings are encrypted to each friend's key; the relay drops them after 15 min. A notification stays up while sharing."
const val SHARE_NEEDS_RECORD = "Record is off. Sharing turns it on so there is a fix to send."

/** Mock 13's share sheet: who, how long, then one button that says exactly what it will do. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(ui: CrewUi, actions: CrewActions, onClose: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = Poster.Stock, contentColor = Poster.Ink, shape = RectangleShape, dragHandle = null) {
        ShareSheetBody(ui, actions, onClose, Modifier.navigationBarsPadding())
    }
}

@Composable
fun ShareSheetBody(ui: CrewUi, actions: CrewActions, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val friends = ui.shareable
    var chosen by rememberSaveable { mutableStateOf(ui.share?.friendIds ?: friends.map { it.deviceId }.toSet()) }
    var length by rememberSaveable { mutableStateOf(ShareLength.HOUR) }
    val recordOff = !ui.recording.active(ui.now)
    Column(modifier.fillMaxWidth().background(Poster.Stock).padding(horizontal = 20.dp, vertical = 24.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(32.dp).height(4.dp).background(Poster.Ink))
        Spacer(Modifier.height(24.dp))
        CapsText("Share my location", size = 11.sp)
        Spacer(Modifier.height(8.dp))
        DisplayText("Who, how long", 40.sp)
        Spacer(Modifier.height(20.dp))
        if (friends.isEmpty()) Text("Turn on Live or Sonar for a friend first; only they can see you.", color = Poster.Ink3, style = PosterType.Lede)
        for (f in friends) {
            val on = f.deviceId in chosen
            Row(
                Modifier.fillMaxWidth().toggleable(value = on, role = Role.Switch) { chosen = if (it) chosen + f.deviceId else chosen - f.deviceId }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).background(Poster.Stock2).border(2.dp, Poster.Ink), contentAlignment = Alignment.Center) { DisplayText(f.initial, 24.sp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    DisplayText(f.name, 22.sp, maxLines = 1)
                    val sub = when {
                        ui.sharingWith(f) -> "Sharing · ${ui.share!!.elapsedLabel(ui.now)}"
                        f.grants.livePrecise -> "Precise · ±12 m"
                        f.grants.liveCity -> "City · ~5 km"
                        else -> "Sonar only"
                    }
                    CapsText(sub, color = if (ui.sharingWith(f)) Poster.Red else Poster.Ink3, size = 10.sp)
                }
                InkSwitch(on)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Poster.Stock3))
        }
        Spacer(Modifier.height(24.dp))
        CapsText("How long", size = 11.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (l in ShareLength.entries) {
                val on = l == length
                Box(
                    Modifier.weight(1f).background(if (on) Poster.Ink else Poster.Stock).border(2.dp, Poster.Ink)
                        .selectable(selected = on, role = Role.RadioButton) { length = l }.padding(horizontal = 4.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(LENGTH_LABEL.getValue(l).uppercase(), color = if (on) Poster.Stock else Poster.Ink, style = PosterType.Label.copy(fontSize = 11.sp), maxLines = 1) }
            }
        }
        Spacer(Modifier.height(24.dp))
        if (recordOff) { Text(SHARE_NEEDS_RECORD, color = Poster.Red, style = PosterType.Label); Spacer(Modifier.height(12.dp)) }
        InkButton(
            if (recordOff) "Start Record & share" else LENGTH_VERB.getValue(length),
            { if (recordOff) actions.startRecording(); actions.startShare(chosen, length); onClose() },
            Modifier.fillMaxWidth(), enabled = chosen.isNotEmpty(),
        )
        if (ui.sharing) {
            Spacer(Modifier.height(10.dp))
            InkButton("Stop sharing", { actions.stopShare(); onClose() }, Modifier.fillMaxWidth(), quiet = true)
        }
        Spacer(Modifier.height(16.dp))
        Text(SHARE_CAPTION, color = Poster.Ink3, style = PosterType.Label)
    }
}

/** The mock's blocky switch: a red block slid right when on, ink slid left when off. */
@Composable
fun InkSwitch(on: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.width(40.dp).height(24.dp).border(2.dp, Poster.Ink).padding(3.dp), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.width(16.dp).height(14.dp).background(if (on) Poster.Red else Poster.Ink))
    }
}

/** The persistent mustard strip: "You're sharing · city-level · Marta, Ásta · 42 min", with Stop. */
@Composable
fun SharingBanner(ui: CrewUi, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val s = ui.share?.takeIf { it.active(ui.now) } ?: return
    val with = ui.friends.filter { it.deviceId in s.friendIds }
    val grade = if (with.any { it.grants.livePrecise }) "precise" else "city-level"
    Row(modifier.fillMaxWidth().background(Poster.Mustard).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            DisplayText("You're sharing", 18.sp)
            CapsText("$grade · ${with.joinToString(", ") { it.name }} · ${s.elapsedLabel(ui.now)}", color = Poster.Ink, size = 9.sp)
        }
        Box(Modifier.background(Poster.Ink).clickable(role = Role.Button, onClick = onStop).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("STOP", color = Poster.Stock, style = PosterType.Display.copy(fontSize = 14.sp))
        }
    }
}
