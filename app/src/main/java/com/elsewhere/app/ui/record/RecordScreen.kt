package com.elsewhere.app.ui.record

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.elsewhere.app.record.RecordState
import com.elsewhere.app.record.TicketTone
import com.elsewhere.app.ui.world.Lede
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.Tone
import com.elsewhere.app.ui.world.motionOn
import com.elsewhere.app.ui.world.n0
import com.elsewhere.app.ui.world.plural
import androidx.hilt.navigation.compose.hiltViewModel
import com.elsewhere.app.highlights.HighlightsViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.ui.world.Bill
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.LocalMuted
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.Rule
import com.elsewhere.app.ui.world.date

/** What the screen can do; the route wires these to launchers and intents, snapshots pass no-ops. */
data class RecordActions(
    val onAllow: (Perm) -> Unit = {}, val onOpenSettings: () -> Unit = {}, val onBattery: () -> Unit = {},
    val onStart: () -> Unit = {}, val onStop: () -> Unit = {}, val onPause: () -> Unit = {}, val onClose: () -> Unit = {},
)

const val DISCLOSURE = "Elsewhere records where your phone goes, on this phone only, so your poster keeps growing after Google's export ends. Nothing is uploaded."

@Composable
fun RecordRoute(onClose: () -> Unit, vm: RecordViewModel = hiltViewModel()) {
    BackHandler { onClose() }
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
    val highlights = hiltViewModel<HighlightsViewModel>()
    val highlightsAt by highlights.at.collectAsStateWithLifecycle()
    RecordScreen(ui, highlightsAt = highlightsAt, onHighlightsAt = highlights::set, actions = RecordActions(
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
fun RecordScreen(ui: RecordUi, actions: RecordActions, modifier: Modifier = Modifier, scroll: Boolean = true, highlightsAt: Int? = null, onHighlightsAt: (Int?) -> Unit = {}) {
    Column(modifier.fillMaxWidth().background(Poster.Stock).then(if (scroll) Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()) else Modifier)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Poster.Ink), contentAlignment = Alignment.Center) { DisplayText("E", 26.sp, color = Poster.Stock) }
            Spacer(Modifier.weight(1f))
            CapsText("Close", Modifier.clickable(onClick = actions.onClose).padding(8.dp), color = Poster.Ink, size = 11.sp)
        }
        TicketBill(ui, actions)
        DetailsBill(ui, actions)
        if (!ui.batteryOk && ui.phase != Phase.DISCLOSURE) BatteryBill(actions)
        HighlightsBill(highlightsAt, onHighlightsAt)
    }
}

internal const val DETAILS_TAG = "record-details"

/**
 * The hero: one boarding-pass ticket. Headline and the fixes · last line on the body, the buttons on the stub.
 * Before location is allowed the body carries the disclosure, since that is what the first tap agrees to.
 */
@Composable
internal fun TicketBill(ui: RecordUi, actions: RecordActions) {
    val t = ui.ticket
    val state = ui.state
    val color = when (t.tone) { TicketTone.RED -> Poster.Red; TicketTone.INK -> Poster.Ink; TicketTone.GHOST -> Poster.Ink3 }
    Bill(first = true) {
        CapsText("Recording · your record, live", color = Poster.Ink)
        Spacer(Modifier.height(16.dp))
        Pass(Modifier.fillMaxWidth(), stub = {
            when (state) {
                RecordState.NEEDS_PERMISSION -> InkButton("Allow location", { actions.onAllow(Perm.FINE) }, Modifier.fillMaxWidth(), red = true)
                RecordState.OFF -> InkButton("Start recording", actions.onStart, Modifier.fillMaxWidth(), red = true, enabled = ui.canStart)
                RecordState.RECORDING -> {
                    InkButton("Pause for 24 h", actions.onPause, quiet = true)
                    InkButton("Stop recording", actions.onStop, red = true)
                }
                RecordState.PAUSED -> {
                    InkButton("Resume", actions.onStart, red = true)
                    InkButton("Stop", actions.onStop, quiet = true)
                }
            }
        }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CapsText("Boarding pass · status", Modifier.weight(1f), size = 11.sp)
                Spacer(Modifier.width(12.dp))
                if (state == RecordState.RECORDING) { PulseDot(); Spacer(Modifier.width(6.dp)) }
                Text(t.headline, color = color, style = PosterType.Caps.copy(fontSize = 11.sp), softWrap = false, maxLines = 1)
            }
            Spacer(Modifier.height(14.dp))
            Text(t.headline, color = color, style = PosterType.Display.copy(fontSize = 56.sp, lineHeight = 0.9.em), maxLines = 2)
            Spacer(Modifier.height(10.dp))
            Text("${t.fixes} · ${t.last}", color = Poster.Ink, style = PosterType.Label.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
            val note = when (state) {
                RecordState.NEEDS_PERMISSION -> DISCLOSURE
                RecordState.OFF -> if (ui.canStart) "Location is allowed all the time. Start, and the poster grows on its own." else "Location is allowed while the app is open. For the record to grow in your pocket, allow it all the time under Details."
                RecordState.PAUSED -> "Paused until ${date(ui.recording.pausedUntil)}. The record picks up again by itself."
                RecordState.RECORDING -> "Since ${date(ui.recording.since)} · ${n0(ui.stats?.stays ?: 0)} ${plural(ui.stats?.stays ?: 0, "stay")} · on this phone only, nothing uploaded."
            }
            Spacer(Modifier.height(14.dp))
            Text(note, color = LocalMuted.current, style = PosterType.Label.copy(fontSize = 13.sp))
        }
    }
}

/** The recording light: a red dot breathing once a second, still under reduced motion. */
@Composable
private fun PulseDot(modifier: Modifier = Modifier) {
    val alpha: State<Float> = if (motionOn()) {
        rememberInfiniteTransition(label = "rec").animateFloat(1f, 0.25f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dot")
    } else rememberUpdatedState(1f)
    Box(modifier.size(8.dp).graphicsLayer { this.alpha = alpha.value }.background(Poster.Red, CircleShape).semantics { contentDescription = "Recording" })
}

/**
 * The permission checklist behind a DETAILS row: folded by default, open on its own while anything is missing.
 * The reader's own tap wins over the automatic fold once they have touched it.
 */
@Composable
internal fun DetailsBill(ui: RecordUi, actions: RecordActions) {
    var open by rememberSaveable(ui.missing) { mutableStateOf(ui.missing) }
    Bill {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.testTag(DETAILS_TAG)
                .semantics { stateDescription = if (open) "Details shown" else "Details hidden" }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CapsText("Details · what Android asks for", color = Poster.Ink)
            Spacer(Modifier.weight(1f))
            if (ui.missing) DisplayText("!", 22.sp, color = Poster.Red)
            Spacer(Modifier.width(8.dp))
            DisplayText(if (open) "▴" else "▾", 22.sp)
        }
        if (open) {
            Spacer(Modifier.height(16.dp))
            PermissionRows(ui, actions)
        }
    }
}

@Composable
internal fun PermissionRows(ui: RecordUi, actions: RecordActions) {
    Column(Modifier.fillMaxWidth()) {
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
