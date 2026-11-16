package com.elsewhere.app.ui.crew

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.crew.Friend
import com.elsewhere.app.crew.ShareCode
import com.elsewhere.app.sonar.PendingCrew
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterTextField
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.TickRow
import com.elsewhere.app.ui.world.date

const val CREW_LEDE = "People who can see your year. Nothing is shared until you turn it on, per person."
const val SONAR_COPY = "Ping us both when we're within a mile and neither of us is usually there."
const val CODE_CAPTION = "A code pairs two devices; it never carries your location or your record. Both confirm before anything is shared."
const val NO_RELAY = "Set a relay in Settings to pair"

@Composable
fun CrewRoute(onBack: () -> Unit, vm: CrewViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val pending by PendingCrew.code.collectAsStateWithLifecycle()
    LaunchedEffect(ui?.relaySet) { if (ui?.relaySet == true) vm.ensureCode() }
    // A pair link lands here already claimed: the visitor only has to confirm.
    LaunchedEffect(pending) { pending?.let { PendingCrew.code.value = null; vm.claim(it) } }
    CrewScreen(
        ui, vm, onBack,
        onScan = { scanPairCode(ctx, vm::claim) { } },
        onShareCode = { sharePairCode(ctx, it) },
    )
}

/** The whole Crew page over the app; blank stock until the stores have been read. `scroll = false` lays it out at full height for snapshots. */
@Composable
fun CrewScreen(
    ui: CrewUi?, actions: CrewActions, onBack: () -> Unit, modifier: Modifier = Modifier, scroll: Boolean = true,
    onScan: (() -> Unit)? = null, onShareCode: ((String) -> Unit)? = null, shareOpen: Boolean = false,
) {
    BackHandler { onBack() }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var qr by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf(shareOpen) }
    Column(modifier.fillMaxWidth().background(Poster.Stock).then(if (scroll) Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()) else Modifier)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "←",
                Modifier.clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" }.padding(horizontal = 8.dp, vertical = 4.dp),
                color = Poster.Ink, style = PosterType.Display.copy(fontSize = 32.sp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                CapsText("Crew · ${ui?.friends?.size ?: 0} ${if (ui?.friends?.size == 1) "friend" else "friends"}", size = 10.sp)
                DisplayText("Crew", 56.sp)
            }
        }
        if (ui != null) {
            SharingBanner(ui, onStop = actions::stopShare)
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(8.dp))
                Text(CREW_LEDE, color = Poster.Ink2, style = PosterType.Lede)
                Spacer(Modifier.height(24.dp))
                FriendGrid(ui, selected, onSelect = { selected = if (selected == it) null else it })
                val f = ui.friends.firstOrNull { it.deviceId == selected }
                if (f != null) { Spacer(Modifier.height(24.dp)); FriendDetail(ui, f, actions) }
                Spacer(Modifier.height(32.dp))
                if (ui.shareable.isNotEmpty()) {
                    InkButton("Share location", { sheet = true }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(32.dp))
                }
                if (ui.relaySet) PairBill(ui, actions, onShowQr = { qr = true }, onScan = onScan, onShareCode = onShareCode)
                else Text(NO_RELAY, color = Poster.Red, style = PosterType.Lede)
                Spacer(Modifier.height(64.dp))
            }
        }
    }
    val pair = ui?.pair
    if (pair is PairState.Confirm) ConfirmPair(pair.pending.name, pair.pending.fingerprint, onConfirm = actions::confirmPair, onDismiss = actions::dismissPair)
    if (qr && ui?.code != null) Dialog(onDismissRequest = { qr = false }) { QrSheet(ui.code, ui.now, onClose = { qr = false }) }
    if (sheet && ui != null) ShareSheet(ui, actions, onClose = { sheet = false })
}

@Composable
private fun FriendGrid(ui: CrewUi, selected: String?, onSelect: (String) -> Unit) {
    val cards: List<Friend?> = ui.friends + listOf<Friend?>(null)
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        for (row in cards.chunked(3)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (f in row) Box(Modifier.weight(1f)) { if (f != null) FriendCard(ui, f, f.deviceId == selected) { onSelect(f.deviceId) } else AddCard() }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Mock 12's passport photo: a bordered square with the initial, the last known city along the bottom, a red dot while live. */
@Composable
private fun FriendCard(ui: CrewUi, f: Friend, selected: Boolean, onClick: () -> Unit) {
    val fix = ui.fixes[f.deviceId]
    val live = fix != null && !fix.stale(ui.now)
    Column(Modifier.clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "Friend ${f.name}" }) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.84f).background(if (selected) Poster.Mustard else Poster.Stock2).border(if (selected) 3.dp else 2.dp, Poster.Ink)) {
            DisplayText(f.initial, 44.sp, Modifier.align(Alignment.Center), color = if (fix == null) Poster.Ink3 else Poster.Ink)
            if (live) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).width(10.dp).height(10.dp).background(Poster.Red))
            Box(Modifier.align(Alignment.BottomStart).padding(6.dp).background(Poster.Stock).border(1.dp, Poster.Ink).padding(horizontal = 6.dp, vertical = 3.dp)) {
                CapsText(fix?.city ?: "paired", color = Poster.Ink, size = 8.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        DisplayText(f.name, 20.sp, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        CapsText(status(ui, f), size = 8.sp)
    }
}

private fun status(ui: CrewUi, f: Friend): String {
    val g = f.grants
    val parts = buildList {
        if (ui.sharingWith(f)) add("Live ${ui.share!!.elapsedLabel(ui.now)}")
        else if (g.livePrecise) add("Live · precise") else if (g.liveCity) add("Live · city")
        if (g.sonar) add("Sonar")
    }
    return if (parts.isEmpty()) "Nothing yet" else parts.joinToString(" · ")
}

@Composable
private fun AddCard() {
    Column {
        Box(Modifier.fillMaxWidth().aspectRatio(0.84f).border(2.dp, Poster.Ink3), contentAlignment = Alignment.Center) { DisplayText("+", 44.sp, color = Poster.Ink3) }
        Spacer(Modifier.height(8.dp))
        DisplayText("Add", 20.sp, color = Poster.Ink3)
        Spacer(Modifier.height(4.dp))
        CapsText("Code or QR", size = 8.sp)
    }
}

/** What one friend gets: the three grants, Sonar's mute, and the way out. */
@Composable
private fun FriendDetail(ui: CrewUi, f: Friend, actions: CrewActions) {
    val muted = f.muted(ui.now)
    Column(Modifier.fillMaxWidth().border(2.dp, Poster.Ink).padding(16.dp)) {
        CapsText("${f.name} · what ${f.name} sees", size = 10.sp)
        Spacer(Modifier.height(8.dp))
        Text(f.fingerprint, color = Poster.Ink3, style = PosterType.Label.copy(fontFamily = FontFamily.Monospace))
        CapsText("Paired ${date(f.addedAt / 1000)}", size = 9.sp)
        Spacer(Modifier.height(12.dp))
        TickRow(f.grants.liveCity, { actions.setGrants(f.deviceId, f.grants.copy(liveCity = it)) }, "Live · city")
        TickRow(f.grants.livePrecise, { actions.setGrants(f.deviceId, f.grants.copy(livePrecise = it)) }, "Live · precise")
        // Sonar implies city-level live sharing; switching it off leaves Live · city as it was.
        TickRow(f.grants.sonar, { actions.setGrants(f.deviceId, if (it) f.grants.copy(sonar = true, liveCity = true) else f.grants.copy(sonar = false)) }, "Sonar")
        Text(SONAR_COPY, Modifier.padding(start = 34.dp), color = Poster.Ink3, style = PosterType.Label)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (f.grants.sonar) {
                if (muted) Chip("Muted for today · unmute") { actions.unmute(f.deviceId) }
                else Chip("Mute Sonar for today") { actions.muteToday(f.deviceId) }
            }
            Spacer(Modifier.weight(1f))
            InkButton("Remove", { actions.remove(f.deviceId) }, quiet = true, red = true)
        }
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Box(Modifier.border(2.dp, Poster.Ink).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        CapsText(text, color = Poster.Ink, size = 9.sp)
    }
}

/** The code bill: the big code with its countdown, three ways to pass it, and a slot for a friend's. */
@Composable
private fun PairBill(ui: CrewUi, actions: CrewActions, onShowQr: () -> Unit, onScan: (() -> Unit)?, onShareCode: ((String) -> Unit)?) {
    var typed by rememberSaveable { mutableStateOf("") }
    val code = ui.code
    CapsText("Your share code", color = Poster.Ink, size = 11.sp)
    Spacer(Modifier.height(6.dp))
    CapsText(if (code != null) "Expires in ${code.remaining(ui.now)}" else "Minting…", size = 9.sp)
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().border(3.dp, Poster.Ink).padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        DisplayText(code?.code ?: "PT-···-····", 40.sp, Modifier.alpha(if (code == null) 0.4f else 1f))
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InkButton("Share", { code?.let { onShareCode?.invoke(it.code) } }, Modifier.weight(1f), enabled = code != null && onShareCode != null)
        InkButton("Show QR", onShowQr, Modifier.weight(1f), quiet = true, enabled = code != null)
        InkButton("Scan", { onScan?.invoke() }, Modifier.weight(1f), quiet = true, enabled = onScan != null)
    }
    Spacer(Modifier.height(8.dp))
    Text("New code", Modifier.clickable(role = Role.Button, onClick = actions::newCode).padding(vertical = 4.dp), color = Poster.Ink3, style = PosterType.Label)
    Spacer(Modifier.height(28.dp))
    PosterTextField(typed, { typed = it }, Modifier.fillMaxWidth(), placeholder = "PT-XXX-XXXX", label = "Enter a friend's code", mono = true)
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InkButton("Pair", { actions.claim(typed) }, enabled = ui.pair !is PairState.Working && ShareCode.parse(typed) != null)
        when (val p = ui.pair) {
            PairState.Working -> Text("Asking the relay…", color = Poster.Ink3, style = PosterType.Label)
            is PairState.Failed -> Text(p.message, color = Poster.Red, style = PosterType.Label)
            else -> {}
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(CODE_CAPTION, color = Poster.Ink3, style = PosterType.Label)
}

/** Both sides see the other's name and fingerprint before a key is kept. */
@Composable
fun ConfirmPair(name: String, fingerprint: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) { ConfirmPairBody(name, fingerprint, onConfirm, onDismiss) }
}

@Composable
fun ConfirmPairBody(name: String, fingerprint: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Pass(Modifier.fillMaxWidth().padding(16.dp)) {
        CapsText("Pairing")
        Spacer(Modifier.height(12.dp))
        DisplayText("Pair with $name?", 40.sp)
        Spacer(Modifier.height(16.dp))
        Text("Their fingerprint is", color = Poster.Ink2, style = PosterType.Lede)
        Text(fingerprint, color = Poster.Ink, style = PosterType.Display.copy(fontSize = 28.sp, fontFamily = FontFamily.Monospace))
        Text("Check it matches on their screen. Nothing is shared until you turn it on for them.", color = Poster.Ink2, style = PosterType.Lede)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InkButton("Pair", onConfirm)
            InkButton("Not now", onDismiss, quiet = true)
        }
    }
}

/** Mock 12's second phone: the code as a QR to hold up, with the code typed beneath it. */
@Composable
fun QrSheet(code: ShareCode, now: Long, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Poster.Stock).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CapsText("Add a friend", size = 11.sp)
        Spacer(Modifier.height(8.dp))
        DisplayText("Show this, or scan theirs", 32.sp)
        Spacer(Modifier.height(24.dp))
        QrCode(ShareCode.link(code.code), Modifier.fillMaxWidth(0.7f).border(3.dp, Poster.Ink).padding(14.dp))
        Spacer(Modifier.height(24.dp))
        CapsText("Your code · ${code.remaining(now)}", size = 9.sp)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().border(3.dp, Poster.Ink).padding(vertical = 14.dp), contentAlignment = Alignment.Center) { DisplayText(code.code, 36.sp) }
        Spacer(Modifier.height(20.dp))
        InkButton("Done", onClose, Modifier.fillMaxWidth(), quiet = true)
    }
}
