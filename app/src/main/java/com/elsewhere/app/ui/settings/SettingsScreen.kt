package com.elsewhere.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.ask.DEFAULT_CUSTOM_BASE_URL
import com.elsewhere.app.ask.DEFAULT_MODEL
import com.elsewhere.app.ask.LlmConfig
import com.elsewhere.app.ask.LlmProvider
import com.elsewhere.app.data.MapProvider
import com.elsewhere.app.data.Units
import com.elsewhere.app.ingest.Probe
import com.elsewhere.app.map.Sdk
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterTextField
import com.elsewhere.app.ui.world.PosterType

const val LLM_CAPTION = "Your key is stored on this phone only and is sent straight to the provider you choose."

val LLM_PROVIDER_LABEL: Map<LlmProvider, String> = mapOf(
    LlmProvider.OPENAI to "OpenAI", LlmProvider.ANTHROPIC to "Anthropic", LlmProvider.GEMINI to "Gemini", LlmProvider.CUSTOM to "Ollama / custom",
)

private val MAP_PROVIDER_LABEL: Map<MapProvider, String> = mapOf(MapProvider.AUTO to "Auto", MapProvider.GOOGLE_TILES to "Google tiles", MapProvider.FREE to "Free")
private val UNITS_LABEL: Map<Units, String> = mapOf(Units.KM to "km", Units.MI to "mi")

@Composable
fun SettingsRoute(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    SettingsScreen(ui, vm, onBack)
}

/** The whole settings page over the app; blank stock until the stores have been read. `scroll = false` lays it out at full height for snapshots. */
@Composable
fun SettingsScreen(ui: SettingsUi?, actions: SettingsActions, onBack: () -> Unit, modifier: Modifier = Modifier, scroll: Boolean = true, sdk: Boolean = Sdk.available) {
    BackHandler { onBack() }
    Column(modifier.fillMaxWidth().background(Poster.Stock).then(if (scroll) Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()) else Modifier)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "←",
                Modifier.clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" }.padding(horizontal = 8.dp, vertical = 4.dp),
                color = Poster.Ink, style = PosterType.Display.copy(fontSize = 32.sp),
            )
            Spacer(Modifier.width(8.dp))
            DisplayText("Settings", 56.sp)
        }
        if (ui != null) Column(Modifier.padding(horizontal = 20.dp)) {
            GoogleMaps(ui, actions, sdk)
            Spacer(Modifier.height(48.dp))
            AskElsewhere(ui, actions)
            Spacer(Modifier.height(48.dp))
            UnitsSection(ui, actions)
            Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
private fun H2(text: String) {
    DisplayText(text, 32.sp)
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(3.dp).background(Poster.Ink))
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun GoogleMaps(ui: SettingsUi, actions: SettingsActions, sdk: Boolean) {
    var key by rememberSaveable { mutableStateOf(ui.maps.mapsKey) }
    var mapId by rememberSaveable { mutableStateOf(ui.maps.mapId) }
    H2("Google Maps")
    SecretField(key, { key = it }, label = "Google Maps key", placeholder = "AIza…")
    Spacer(Modifier.height(24.dp))
    PosterTextField(mapId, { mapId = it }, Modifier.fillMaxWidth(), placeholder = "none", label = "Map ID · optional", mono = true)
    Spacer(Modifier.height(24.dp))
    CapsText("Map provider", size = 10.sp)
    Spacer(Modifier.height(8.dp))
    Segmented(MapProvider.entries.map { it to MAP_PROVIDER_LABEL.getValue(it) }, ui.maps.mapProvider, onSelect = actions::setMapProvider)
    Spacer(Modifier.height(24.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InkButton("Save", { actions.saveKey(key, mapId) }, enabled = key.isNotBlank() && ui.keyCheck != KeyCheck.Checking)
        KeyStatus(ui, sdk)
    }
}

@Composable
private fun KeyStatus(ui: SettingsUi, sdk: Boolean) {
    val (text, color) = when (val k = ui.keyCheck) {
        KeyCheck.Checking -> "Checking your key…" to Poster.Ink3
        is KeyCheck.Done -> {
            val r = k.report
            if (r.accepted) "Key accepted · Places ✓ · Map tiles ${if (r.tiles == Probe.Ok) "✓" else "not needed"}" to Poster.Green
            else {
                val why = listOfNotNull(r.places, r.tiles.takeUnless { sdk }).filterIsInstance<Probe.Failed>().firstOrNull()?.message
                "Key not accepted${if (why != null) " · $why" else ""}" to Poster.Red
            }
        }
        KeyCheck.Idle -> when {
            ui.maps.mapsKey.isBlank() -> "No key saved" to Poster.Ink3
            ui.maps.keyCheckedAt == 0L -> "Key saved, not yet checked" to Poster.Ink3
            ui.maps.keyPlaces && (ui.maps.keyTiles || sdk) -> "Key accepted" to Poster.Green
            else -> "Key not accepted" to Poster.Red
        }
    }
    Text(text, color = color, style = PosterType.Label)
}

@Composable
private fun AskElsewhere(ui: SettingsUi, actions: SettingsActions) {
    // Plain fields rather than the config itself, so the saved state survives a process death.
    var provider by rememberSaveable { mutableStateOf(ui.llm.provider) }
    var key by rememberSaveable { mutableStateOf(ui.llm.key) }
    var model by rememberSaveable { mutableStateOf(ui.llm.model) }
    var baseUrl by rememberSaveable { mutableStateOf(ui.llm.baseUrl) }
    val c = LlmConfig(provider, key, model, baseUrl)
    val set = { next: LlmConfig -> provider = next.provider; key = next.key; model = next.model; baseUrl = next.baseUrl; actions.setLlm(next) }
    H2("Ask Elsewhere")
    CapsText("Provider", size = 10.sp)
    Spacer(Modifier.height(8.dp))
    Segmented(LlmProvider.entries.map { it to LLM_PROVIDER_LABEL.getValue(it) }, c.provider, perRow = 2) { p ->
        // A fresh custom endpoint starts at the local Ollama default, so TEST has somewhere to go.
        set(c.copy(provider = p, baseUrl = if (p == LlmProvider.CUSTOM) c.baseUrl.ifBlank { DEFAULT_CUSTOM_BASE_URL } else c.baseUrl))
    }
    Spacer(Modifier.height(24.dp))
    if (c.provider != LlmProvider.CUSTOM) {
        SecretField(c.key, { set(c.copy(key = it)) }, label = "API key", placeholder = "paste your key")
        Spacer(Modifier.height(24.dp))
    }
    PosterTextField(c.model, { set(c.copy(model = it)) }, Modifier.fillMaxWidth(), placeholder = DEFAULT_MODEL.getValue(c.provider), label = "Model", mono = true)
    if (c.provider == LlmProvider.CUSTOM) {
        Spacer(Modifier.height(24.dp))
        PosterTextField(c.baseUrl, { set(c.copy(baseUrl = it)) }, Modifier.fillMaxWidth(), placeholder = DEFAULT_CUSTOM_BASE_URL, label = "Base URL", mono = true)
    }
    Spacer(Modifier.height(16.dp))
    Text(LLM_CAPTION, color = Poster.Ink3, style = PosterType.Label)
    Spacer(Modifier.height(24.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InkButton("Test", { actions.testLlm(c) }, enabled = ui.test != LlmTest.Testing && (c.provider == LlmProvider.CUSTOM || c.key.isNotBlank()))
        when (val t = ui.test) {
            LlmTest.Idle -> {}
            LlmTest.Testing -> Text("Asking…", color = Poster.Ink3, style = PosterType.Label)
            is LlmTest.Ok -> Text("Connected · ${t.model}", color = Poster.Green, style = PosterType.Label)
            is LlmTest.Failed -> SelectionContainer { Text(t.message, color = Poster.Red, style = PosterType.Label.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.sp)) }
        }
    }
}

@Composable
private fun UnitsSection(ui: SettingsUi, actions: SettingsActions) {
    H2("Units")
    Segmented(Units.entries.map { it to UNITS_LABEL.getValue(it) }, ui.units, onSelect = actions::setUnits)
}

/** A caps label with a Show/Hide switch on the right, over a dotted-out mono field. */
@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: String, placeholder: String) {
    var shown by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CapsText(label, Modifier.weight(1f), size = 10.sp)
            CapsText(if (shown) "Hide" else "Show", Modifier.clickable(role = Role.Button) { shown = !shown }.padding(horizontal = 8.dp, vertical = 4.dp), color = Poster.Ink, size = 10.sp)
        }
        Spacer(Modifier.height(4.dp))
        PosterTextField(value, onChange, Modifier.fillMaxWidth(), placeholder = placeholder, mono = true, hidden = !shown)
    }
}

/** A printed radio strip: the chosen cell in ink, the rest outlined; `perRow` wraps a long list onto more rows. */
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, perRow: Int = options.size, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in options.chunked(perRow)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((v, label) in row) {
                val on = v == selected
                Box(
                    Modifier.weight(1f).background(if (on) Poster.Ink else Poster.Stock).border(2.dp, Poster.Ink)
                        .selectable(selected = on, role = Role.RadioButton) { onSelect(v) }.padding(horizontal = 6.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (on) Poster.Stock else Poster.Ink, style = PosterType.Label.copy(fontSize = 11.sp), maxLines = 1)
                }
            }
        }
    }
}
