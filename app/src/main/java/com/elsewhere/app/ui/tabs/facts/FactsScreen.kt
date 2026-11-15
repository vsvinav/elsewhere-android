package com.elsewhere.app.ui.tabs.facts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.analytics.Fact
import com.elsewhere.app.analytics.Figure
import com.elsewhere.app.analytics.Kind
import com.elsewhere.app.data.Units
import com.elsewhere.app.ui.world.Bill
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Field
import com.elsewhere.app.ui.world.Foot
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Lede
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.Share
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.motionOn
import com.elsewhere.app.ui.world.n0
import com.elsewhere.app.share.SITE
import com.elsewhere.app.share.bedText
import com.elsewhere.app.ui.nav.door
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.RecordStamp
import com.elsewhere.app.ui.world.figures.Fig
import com.elsewhere.app.highlights.PendingHighlight
import com.elsewhere.app.ui.tabs.highlights.OpenDoor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Port of web/src/tabs/Facts/Facts.tsx: the Play deck up top, every fact printed as a pass under a disclosure.

/** Figures that read at 110dp go beside the text; the wide ones sit under it. */
private fun wide(f: Figure?) = f is Figure.Scale || f is Figure.Beds

/** The doors and the key step come from the scaffold, which owns the range and the tabs; no second RangeViewModel here. */
@Composable
fun FactsTab(onOpen: OpenDoor = { _, _, _ -> }, onKeyStep: (() -> Unit)? = null, vm: FactsViewModel = hiltViewModel(), passport: @Composable () -> Unit = { PassportTab() }) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    // A notification tap names the day its card was printed for; taken once, so the next visit is today's again.
    val pending = remember { PendingHighlight.date.value.also { PendingHighlight.date.value = null } }
    ui?.let { FactsScreen(if (pending != null) it.copy(today = pending) else it, onOpen = onOpen, onKeyStep = onKeyStep, passport = passport) } ?: Box(Modifier.fillMaxSize().background(Poster.Stock))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FactsScreen(ui: FactsUi, modifier: Modifier = Modifier, scroll: Boolean = true, onOpen: OpenDoor = { _, _, _ -> }, onKeyStep: (() -> Unit)? = null, passport: (@Composable () -> Unit)? = null) {
    var seed by rememberSaveable { mutableIntStateOf(0) }
    var only by rememberSaveable { mutableStateOf<Kind?>(null) }
    var open by rememberSaveable { mutableStateOf(false) }
    val list = arrange(ui.all, seed).filter { only == null || it.kind == only }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val motion = motionOn()
    var listY by remember { mutableIntStateOf(0) }
    var passY by remember { mutableIntStateOf(0) }
    val scrollTo: (Int) -> Unit = { y -> scope.launch { if (motion) scrollState.animateScrollTo(y) else scrollState.scrollTo(y) } }
    Column(modifier.fillMaxWidth().background(Poster.Stock).then(if (scroll) Modifier.fillMaxSize().verticalScroll(scrollState) else Modifier)) {
        FactsHero(
            ui,
            onOpen = onOpen,
            onOpenKind = { k -> only = k; open = true; scrollTo(listY) },
            onOpenPassport = { scrollTo(passY) },
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 24.dp).onGloballyPositioned { listY = it.positionInParent().y.roundToInt() }, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            AllFacts(ui.all.size, open) { open = !open }
            if (open) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (k in ui.kinds) KindChip(KIND_LABEL.getValue(k), ui.count(k), only == k) { only = if (only == k) null else k }
                    if (ui.all.size > 1) InkButton("Shuffle", { seed = (System.currentTimeMillis() and 0xffff).toInt() }, quiet = true)
                }
                list.forEachIndexed { i, f -> FactPass(f, i + 1, list.size) }
            }
        }
        Box(Modifier.onGloballyPositioned { passY = it.positionInParent().y.roundToInt() }) { passport?.invoke() }
    }
}

/** The whole list of facts, folded under one rule until asked: the web's "ALL FACTS" disclosure. */
@Composable
internal fun AllFacts(n: Int, open: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = "All facts"; stateDescription = if (open) "Expanded" else "Collapsed" },
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Poster.Ink))
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("ALL FACTS", color = Poster.Ink, style = PosterType.Display.copy(fontSize = 22.sp, lineHeight = 1.em, letterSpacing = 0.06.em))
            CapsText("$n passes ${if (open) "−" else "+"}", size = 11.sp)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Poster.Ink))
    }
}

/** The hero: the Play title, a line about the deck, and the deck itself. */
@Composable
internal fun FactsHero(ui: FactsUi, onOpen: OpenDoor = { _, _, _ -> }, onOpenKind: (Kind) -> Unit = {}, onOpenPassport: () -> Unit = {}) {
    Bill(first = true) {
        Box(Modifier.fillMaxWidth()) {
            DisplayText("Play", 96.sp, misreg = true)
            RecordStamp("Play", "${ui.year}", "live", Modifier.align(Alignment.TopEnd), size = 92.dp)
        }
        Spacer(Modifier.height(28.dp))
        Lede(
            if (ui.all.isNotEmpty()) "Six cards from ${n0(ui.days)} days of record. Each one opens its full pass; the whole list of facts is one tap away."
            else "Nothing to say yet. Widen the range, or wait for the record to fill in.",
        )
        ui.hub?.let {
            Spacer(Modifier.height(30.dp))
            HubDeck(ui, it, onOpen = onOpen, onOpenKind = onOpenKind, onOpenPassport = onOpenPassport)
        }
        Spacer(Modifier.height(24.dp))
        Foot(if (ui.units == Units.MI) "Miles" else "Kilometres", null)
    }
}

@Composable
private fun KindChip(label: String, n: Int, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.background(if (on) Poster.Ink else Poster.Stock).border(2.dp, Poster.Ink)
            .toggleable(value = on, role = Role.Checkbox, onValueChange = { onClick() }).semantics { contentDescription = "Show only $label" }
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        val style = PosterType.Display.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.02.em, lineHeight = 1.em)
        Text(label.uppercase(), color = if (on) Poster.Stock else Poster.Ink, style = style)
        Spacer(Modifier.width(6.dp))
        Text("$n", color = if (on) Poster.Mustard else Poster.Red, style = style)
    }
}

/** One fact printed as a pass: headline, the longer line, its figure, and a stub naming its kind. */
@Composable
internal fun FactPass(f: Fact, index: Int, total: Int) {
    // Mirrors web/src/tabs/Facts: a beds figure shares as the strip, anything else as headline, detail, site.
    val text = { (f.figure as? Figure.Beds)?.let { bedText(it.nights, f.headline.uppercase()) } ?: "${f.headline.uppercase()}\n${f.detail}\n$SITE" }
    Pass(share = Share(f.headline, text), stub = { Field(KIND_LABEL.getValue(f.kind), "$index of $total") }) {
        val red = f.kind == Kind.EXPLORED || f.kind == Kind.RECORD
        Text(f.headline.uppercase(), Modifier.door(f.door), color = if (red) Poster.Red else Poster.Ink, style = PosterType.Display.copy(fontSize = 40.sp, lineHeight = 0.9.em, letterSpacing = (-0.01).em))
        Spacer(Modifier.height(20.dp))
        Text(f.detail, color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 1.4.em))
        // Figures keep their ink text, so they print on the stock body here rather than the ink stub.
        f.figure?.let { fig ->
            Spacer(Modifier.height(28.dp))
            if (wide(fig)) Fig(fig, Modifier.fillMaxWidth()) else Box(Modifier.width(150.dp)) { Fig(fig) }
        }
    }
}
