package com.elsewhere.app.ui.record

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.ui.world.Bill
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Foot
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Lede
import com.elsewhere.app.ui.world.LocalMuted
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.RecordStamp
import com.elsewhere.app.ui.world.Rule
import com.elsewhere.app.ui.world.Tone
import com.elsewhere.app.ui.world.date

/** What the screen can do; the route wires these to launchers and intents, snapshots pass no-ops. */
data class RecordActions(
    val onAllow: (Perm) -> Unit = {}, val onOpenSettings: () -> Unit = {}, val onBattery: () -> Unit = {},
    val onStart: () -> Unit = {}, val onStop: () -> Unit = {}, val onPause: () -> Unit = {}, val onClose: () -> Unit = {},
)

const val DISCLOSURE = "Elsewhere records where your phone goes, on this phone only, so your poster keeps growing after Google's export ends. Nothing is uploaded."

@Composable
fun RecordRoute(onClose: () -> Unit, vm: RecordViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.refresh() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    var asked by remember { mutableStateOf<Perm?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val p = asked ?: return@rememberLauncherForActivityResult
        vm.onResult(p, results[p.permission] == true)
    }
    val settingsIntent = { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    RecordScreen(ui, RecordActions(
        onAllow = { p ->
            asked = p
            // Fine and coarse go together: Android 12 lets the user pick, and coarse alone is useless to us.
            launcher.launch(if (p == Perm.FINE) arrayOf(p.permission, android.Manifest.permission.ACCESS_COARSE_LOCATION) else arrayOf(p.permission))
        },
        onOpenSettings = settingsIntent,
        onBattery = {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { settingsIntent() }
        },
        onStart = vm::start, onStop = vm::stop, onPause = vm::pause, onClose = onClose,
    ))
}

/** `scroll = false` lays the whole sheet out at full height, for snapshots. */
@Composable
fun RecordScreen(ui: RecordUi, actions: RecordActions, modifier: Modifier = Modifier, scroll: Boolean = true) {
    Column(modifier.fillMaxWidth().background(Poster.Stock).then(if (scroll) Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()) else Modifier)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Poster.Ink), contentAlignment = Alignment.Center) { DisplayText("E", 26.sp, color = Poster.Stock) }
            Spacer(Modifier.weight(1f))
            CapsText("Close", Modifier.clickable(onClick = actions.onClose).padding(8.dp), color = Poster.Ink, size = 11.sp)
        }
        when (ui.phase) {
            Phase.DISCLOSURE -> DisclosureBill(actions)
            Phase.PERMISSIONS -> ReadyBill(ui, actions)
            Phase.RECORDING, Phase.PAUSED -> StatusBill(ui, actions)
        }
        PermissionsBill(ui, actions)
        if (!ui.batteryOk && ui.phase != Phase.DISCLOSURE) BatteryBill(actions)
    }
}

@Composable
internal fun DisclosureBill(actions: RecordActions) {
    Bill(first = true) {
        Box(Modifier.fillMaxWidth()) {
            Column {
                DisplayText("Record", 72.sp, misreg = true)
                DisplayText("new travel", 72.sp, color = Poster.Red, misreg = true)
            }
            RecordStamp("On this", "phone", "only", Modifier.align(Alignment.TopEnd), size = 92.dp)
        }
        Spacer(Modifier.height(28.dp))
        Lede(DISCLOSURE)
        Spacer(Modifier.height(16.dp))
        Lede("Google is winding down Timeline exports. From here on, your phone can keep the record itself: stays of five minutes or more become stops, the legs between become routes, in the same ledger your export filled.", color = LocalMuted.current)
        Spacer(Modifier.height(40.dp))
        InkButton("Allow location", { actions.onAllow(Perm.FINE) }, Modifier.fillMaxWidth(), red = true)
        Spacer(Modifier.height(40.dp))
        Foot("Nothing is uploaded", "Stop any time")
    }
}

@Composable
internal fun ReadyBill(ui: RecordUi, actions: RecordActions) {
    Bill(first = true) {
        DisplayText("Ready to", 72.sp, misreg = true)
        DisplayText("record", 72.sp, color = Poster.Red, misreg = true)
        Spacer(Modifier.height(28.dp))
        Lede(if (ui.canStart) "Location is allowed all the time. Start, and the poster grows on its own." else "Location is allowed while the app is open. For the record to grow in your pocket, allow it all the time below.")
        Spacer(Modifier.height(40.dp))
        InkButton("Start recording", actions.onStart, Modifier.fillMaxWidth(), red = true, enabled = ui.canStart)
    }
}

@Composable
internal fun StatusBill(ui: RecordUi, actions: RecordActions) {
    val paused = ui.phase == Phase.PAUSED
    Bill(if (paused) Tone.STOCK else Tone.RED, first = true) {
        Box(Modifier.fillMaxWidth()) {
            Column {
                DisplayText(if (paused) "Paused" else "Recording", 72.sp, color = if (paused) Poster.Ink else Poster.Stock, misreg = true)
            }
            RecordStamp("Live", "${ui.stats?.stays ?: 0}", "stays", Modifier.align(Alignment.TopEnd), color = if (paused) Poster.Red else Poster.Stock, size = 92.dp)
        }
        Spacer(Modifier.height(28.dp))
        Lede(if (paused) "Paused until ${date(ui.recording.pausedUntil)}. The record picks up again by itself." else ui.statusLine)
        Spacer(Modifier.height(40.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (paused) InkButton("Resume", actions.onStart, Modifier.weight(1f))
            else InkButton("Pause for 24 h", actions.onPause, Modifier.weight(1f), quiet = true)
            InkButton("Stop", actions.onStop, Modifier.weight(1f), quiet = !paused, red = paused)
        }
        Spacer(Modifier.height(40.dp))
        Foot("On this phone only", "Nothing uploaded")
    }
}

@Composable
internal fun PermissionsBill(ui: RecordUi, actions: RecordActions) {
    Bill {
        CapsText("Permissions · what Android asks for")
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(Rule).background(Poster.Ink))
            for (row in ui.rows) {
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.perm.title.uppercase(), style = PosterType.Display.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.em))
                        Text(row.perm.note, color = LocalMuted.current, style = PosterType.Label.copy(fontSize = 13.sp))
                    }
                    Spacer(Modifier.width(16.dp))
                    when (row.state) {
                        PermState.GRANTED -> DisplayText("✓", 28.sp, color = Poster.Red)
                        PermState.ASK -> InkButton("Allow", { actions.onAllow(row.perm) }, quiet = true)
                        PermState.SETTINGS -> InkButton("Open settings", { if (row.perm == Perm.BACKGROUND) actions.onAllow(row.perm) else actions.onOpenSettings() }, quiet = true)
                        PermState.LOCKED -> CapsText("After location", size = 11.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(Poster.Ink))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("“All the time” is the one that matters: Android 11 and later sends you to Settings for it, where you pick “Allow all the time”.", color = LocalMuted.current, style = PosterType.Label.copy(fontSize = 13.sp))
    }
}

@Composable
internal fun BatteryBill(actions: RecordActions) {
    Bill(Tone.INK) {
        CapsText("Battery · one more switch")
        Spacer(Modifier.height(16.dp))
        Lede("Android may put Elsewhere to sleep after a while and the record goes quiet. Letting it run unrestricted keeps the fixes coming; a day costs about as much battery as an hour of maps.")
        Spacer(Modifier.height(28.dp))
        InkButton("Allow in background", actions.onBattery, quiet = true)
    }
}
