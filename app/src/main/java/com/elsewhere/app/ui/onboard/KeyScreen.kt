package com.elsewhere.app.ui.onboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ingest.KeyReport
import com.elsewhere.app.ingest.Probe
import com.elsewhere.app.map.Sdk
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterTextField
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.Stamp
import com.elsewhere.app.ui.world.TickRow

/** Everything the key page can do; the view model is the real one, previews pass a no-op. */
interface KeyActions {
    fun saveKey(key: String, mapId: String)
    fun skipKey()
    fun recheckKey()
    fun editKey()
    fun setLimitedAck(on: Boolean)
    fun continueKey()
    fun acceptLimited()
}

const val LIMITED_ACK = "I understand maps and place names will be limited"

/**
 * The optional Google key, checked the moment it is saved: the field, then "Checking your key…",
 * then a customs stamp. A refused or skipped key has to be acknowledged as limited maps before
 * the door moves on, so nobody is left wondering why the names never came. On a build with the
 * Maps SDK the key only has to pass Places, and the page says so rather than marking Map Tiles a failure.
 */
@Composable
fun KeyScreen(state: OnboardState, actions: KeyActions, onCancel: (() -> Unit)?, sdk: Boolean = Sdk.available) {
    var k by rememberSaveable(state.key) { mutableStateOf(state.key) }
    var id by rememberSaveable(state.mapId) { mutableStateOf(state.mapId) }
    OnboardFrame(question = null, onCancel = onCancel) {
        when (state.keyPhase) {
            KeyPhase.Entry -> Entry(k, id, { k = it }, { id = it }, state.editing, actions, sdk)
            KeyPhase.Checking -> Checking(sdk)
            KeyPhase.Result -> Result(state.keyReport, k, id, { k = it }, state.limitedAck, actions, sdk)
        }
    }
}

private const val TILES_NOT_NEEDED = "not needed — this build has Google Maps built in"

@Composable
private fun Entry(k: String, id: String, onKey: (String) -> Unit, onId: (String) -> Unit, editing: Boolean, actions: KeyActions, sdk: Boolean) {
    DisplayText("A Maps key", 64.sp)
    Spacer(Modifier.height(28.dp))
    Text("A Google Maps key draws the maps and names your stops. Skip and we'll use OpenStreetMap.", color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 18.sp))
    Spacer(Modifier.height(32.dp))
    PosterTextField(k, onKey, Modifier.fillMaxWidth(), placeholder = "AIza…", label = "Google Maps key", mono = true)
    Spacer(Modifier.height(24.dp))
    PosterTextField(id, onId, Modifier.fillMaxWidth(), placeholder = "none", label = "Map ID · optional", mono = true)
    Spacer(Modifier.height(16.dp))
    val apis = if (sdk) "Places API (New)" else "Places API (New) and Map Tiles API"
    Text("The key stays on this phone. Enable $apis on it, with Application restrictions set to None.", color = Poster.Ink3, style = PosterType.Label)
    Spacer(Modifier.height(36.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InkButton("Use this key", { actions.saveKey(k, id) }, enabled = k.isNotBlank())
        if (editing && k.isNotBlank()) InkButton("Re-check key", { actions.recheckKey() }, quiet = true)
        else InkButton("Skip", { actions.skipKey() }, quiet = true)
    }
}

@Composable
internal fun Checking(sdk: Boolean = Sdk.available) {
    DisplayText("Checking your key…", 56.sp)
    Spacer(Modifier.height(28.dp))
    Pass(Modifier.fillMaxWidth()) {
        CapsText("Asking Google", color = Poster.Red)
        Spacer(Modifier.height(16.dp))
        ProbeLine("Places API (New)", null)
        Spacer(Modifier.height(8.dp))
        ProbeLine("Map Tiles API", null, needed = !sdk)
        Spacer(Modifier.height(14.dp))
        Text("Two small requests, both free. A few seconds at most.", color = Poster.Ink3, style = PosterType.Label)
    }
}

@Composable
internal fun Result(report: KeyReport?, k: String, id: String, onKey: (String) -> Unit, ack: Boolean, actions: KeyActions, sdk: Boolean = Sdk.available) {
    when {
        report == null -> {
            DisplayText("No key", 64.sp)
            Spacer(Modifier.height(24.dp))
            Text("Without a key the maps come from OpenStreetMap and your stops keep the names in your export: cities, not places or photos.", color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 17.sp))
            Spacer(Modifier.height(28.dp))
            TickRow(ack, actions::setLimitedAck, LIMITED_ACK)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InkButton("Continue with limited maps", { actions.acceptLimited() }, enabled = ack)
                InkButton("Enter a key", { actions.editKey() }, quiet = true)
            }
        }
        report.accepted -> {
            Stamp("Key accepted", color = Poster.Green)
            Spacer(Modifier.height(32.dp))
            DisplayText("Good to go", 64.sp)
            Spacer(Modifier.height(20.dp))
            Text(if (report.tiles == Probe.Ok) "Places ✓ · Map tiles ✓" else "Places ✓ · Map tiles not needed", color = Poster.Green, style = PosterType.Lede.copy(fontSize = 18.sp))
            Spacer(Modifier.height(8.dp))
            Text("Google will draw the maps and name your stops. Usage stays on your own key and within the free monthly credit.", color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 16.sp))
            Spacer(Modifier.height(36.dp))
            InkButton("Continue", { actions.continueKey() })
        }
        else -> {
            Stamp("Key not accepted", color = Poster.Red)
            Spacer(Modifier.height(32.dp))
            DisplayText("Google said no", 56.sp)
            Spacer(Modifier.height(20.dp))
            Pass(Modifier.fillMaxWidth()) {
                ProbeLine("Places API (New)", report.places)
                Spacer(Modifier.height(8.dp))
                ProbeLine("Map Tiles API", report.tiles, needed = !sdk)
                for (f in listOfNotNull(report.places, report.tiles.takeUnless { sdk }).filterIsInstance<Probe.Failed>().distinct()) {
                    Spacer(Modifier.height(14.dp))
                    SelectionContainer { Text(f.message, color = Poster.Ink2, style = PosterType.Label.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 0.sp)) }
                    if (f.hint != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(f.hint, color = Poster.Ink, style = PosterType.Lede.copy(fontSize = 15.sp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            PosterTextField(k, onKey, Modifier.fillMaxWidth(), placeholder = "AIza…", label = "Google Maps key", mono = true)
            Spacer(Modifier.height(24.dp))
            TickRow(ack, actions::setLimitedAck, LIMITED_ACK)
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InkButton("Try again", { actions.saveKey(k, id) }, enabled = k.isNotBlank())
                InkButton("Continue with limited maps", { actions.acceptLimited() }, quiet = true, enabled = ack)
            }
        }
    }
}

/** One API's verdict: a caps name and a tick, a cross, or an ellipsis while the probe runs; muted when this build does not need it. */
@Composable
private fun ProbeLine(name: String, probe: Probe?, needed: Boolean = true) {
    val (mark, color) = when {
        !needed -> "—" to Poster.Ink3
        probe == null -> "…" to Poster.Ink3
        probe == Probe.Ok -> "✓" to Poster.Green
        else -> "✕" to Poster.Red
    }
    Row {
        CapsText(name, Modifier.weight(1f), color = if (needed) Poster.Ink else Poster.Ink3, size = 12.sp)
        Spacer(Modifier.width(12.dp))
        Text(mark, color = color, style = PosterType.Caps.copy(fontSize = 14.sp, letterSpacing = 0.sp))
    }
    if (!needed) Text(TILES_NOT_NEEDED, color = Poster.Ink3, style = PosterType.Label)
}
