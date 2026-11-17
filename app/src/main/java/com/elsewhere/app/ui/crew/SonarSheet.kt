package com.elsewhere.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.record.NowStamp
import com.elsewhere.app.sonar.SonarHit
import com.elsewhere.app.ui.photos.LocalSnapper
import com.elsewhere.app.ui.photos.SnapHost
import com.elsewhere.app.ui.photos.SnapPlace
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType

/** The moment Sonar fires: a sheet over whatever is on screen naming the friend, with the camera one tap away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonarSheet(hit: SonarHit, live: NowStamp?, onDismiss: () -> Unit, onSnap: () -> Unit = {}) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Poster.Stock, contentColor = Poster.Ink, shape = RectangleShape, dragHandle = null) {
        SonarSheetBody(hit, live, onDismiss, onSnap, Modifier.navigationBarsPadding())
    }
}

/** Where the app hangs the one Sonar sheet: over every tab, inside its own snap host so the camera and the photo card work anywhere. */
@Composable
fun SonarSheetHost(vm: SonarSheetViewModel = hiltViewModel(), snap: @Composable (@Composable () -> Unit) -> Unit = { SnapHost(content = it) }) {
    val hit by vm.sheet.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    snap { hit?.let { SonarSheet(it, live, onDismiss = vm::dismiss, onSnap = vm::snapped) } }
}

/** The camera stays open for as long as it takes, so the sheet waits for Dismiss rather than going with the snap. */
@Composable
fun SonarSheetBody(hit: SonarHit, live: NowStamp?, onDismiss: () -> Unit, onSnap: () -> Unit = {}, modifier: Modifier = Modifier) {
    val snapper = LocalSnapper.current
    val name = hit.friend.name
    Column(modifier.fillMaxWidth().background(Poster.Stock).padding(horizontal = 20.dp, vertical = 24.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(32.dp).height(4.dp).background(Poster.Ink))
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CapsText("Sonar", size = 11.sp, color = Poster.Red)
            if (hit.rehearsal) { Spacer(Modifier.width(10.dp)); RedTag(REHEARSAL_TAG) }
        }
        Spacer(Modifier.height(8.dp))
        DisplayText("$name is within a mile", 40.sp)
        Spacer(Modifier.height(16.dp))
        Text(if (hit.rehearsal) "Rehearsal ping — $name is within a mile." else SONAR_SHEET_LINE, color = Poster.Ink2, style = PosterType.Lede)
        Spacer(Modifier.height(24.dp))
        InkButton(
            "Snap a photo together",
            {
                val city = live?.city?.ifBlank { null }
                val place = live?.let { SnapPlace("sonar:${hit.hitId}", city ?: "With $name", it.lat, it.lng, city) }
                snapper?.snap(place, caption = "with $name")
                onSnap()
            },
            Modifier.fillMaxWidth(), red = true,
        )
        Spacer(Modifier.height(10.dp))
        InkButton("Dismiss", onDismiss, Modifier.fillMaxWidth(), quiet = true)
    }
}

const val SONAR_SHEET_LINE = "Neither of you is usually here. Say hi?"
