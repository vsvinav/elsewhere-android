package com.elsewhere.app.ui.tabs.facts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.Share
import com.elsewhere.app.share.SITE
import com.elsewhere.app.share.bedText
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.RecordStamp
import com.elsewhere.app.ui.world.figures.Fig
import com.elsewhere.app.analytics.HighlightState
import com.elsewhere.app.analytics.highlightState
import com.elsewhere.app.analytics.localToday
import com.elsewhere.app.highlights.PendingHighlight
import com.elsewhere.app.ui.shell.RangeViewModel
import com.elsewhere.app.ui.tabs.highlights.HighlightCard
import com.elsewhere.app.ui.tabs.highlights.OpenDoor
import kotlinx.coroutines.launch

// Port of web/src/tabs/Facts/Facts.tsx: one fact on a card up top, the rest printed as passes.

/** Figures that read at 110dp go beside the text; the wide ones sit under it. */
private fun wide(f: Figure?) = f is Figure.Scale || f is Figure.Beds

@Composable
fun FactsTab(vm: FactsViewModel = hiltViewModel(), range: RangeViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    // A notification tap names the day its card was printed for; taken once, so the next visit is today's again.
    val pending = remember { PendingHighlight.date.value.also { PendingHighlight.date.value = null } }
    ui?.let { FactsScreen(if (pending != null) it.copy(today = pending) else it, onOpen = { r, _, _ -> range.setRange(r) }) } ?: Box(Modifier.fillMaxSize().background(Poster.Stock))
}

@Composable
fun FactsScreen(ui: FactsUi, modifier: Modifier = Modifier, scroll: Boolean = true, onOpen: OpenDoor = { _, _, _ -> }) {
    var seed by rememberSaveable { mutableIntStateOf(0) }
    var only by rememberSaveable { mutableStateOf<Kind?>(null) }
    val list = arrange(ui.rest, seed).filter { only == null || it.kind == only }
    Column(modifier.fillMaxWidth().background(Poster.Stock).then(if (scroll) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier)) {
        FactsHero(ui, only, onOnly = { only = if (only == it) null else it }, onShuffle = { seed = (System.currentTimeMillis() and 0xffff).toInt() }, onOpen = onOpen)
        Column(Modifier.padding(horizontal = 12.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            list.forEachIndexed { i, f -> FactPass(f, i + 1, list.size) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun FactsHero(ui: FactsUi, only: Kind?, onOnly: (Kind) -> Unit, onShuffle: () -> Unit, onOpen: OpenDoor = { _, _, _ -> }) {
    val cards = ui.cards
    // "On this day" rides in front of the fact cards when the record has one (the web's HighlightSlot).
    val highlight = ui.raw?.takeIf { highlightState(it, ui.today ?: localToday()) != HighlightState.None }
    val extra = if (highlight != null) 1 else 0
    val pager = rememberPagerState(pageCount = { cards.size + extra })
    val scope = rememberCoroutineScope()
    LaunchedEffect(cards) { if (pager.currentPage >= cards.size + extra) pager.scrollToPage(0) }
    Bill(first = true) {
        Box(Modifier.fillMaxWidth()) {
            Column {
                DisplayText("Fun", 96.sp, misreg = true)
                DisplayText("Facts", 96.sp, color = Poster.Red, misreg = true)
            }
            RecordStamp("Fun facts", "${ui.year}", "live", Modifier.align(Alignment.TopEnd), size = 92.dp)
        }
        Spacer(Modifier.height(28.dp))
        Lede(
            if (ui.all.isNotEmpty()) "${ui.rest.size} things your record says about you, read sideways. Firsts are judged over the whole record, so a January trip stays new when you cut the range down."
            else "Nothing to say yet. Widen the range, or wait for the record to fill in.",
        )
        val n = cards.size + extra
        if (n > 0) {
            Spacer(Modifier.height(30.dp))
            val cur = pager.currentPage.coerceIn(0, n - 1)
            val star = cards.getOrNull(cur - extra)
            Field(if (star == null) "On this day" else if (star.card) "Side note" else KIND_LABEL.getValue(star.kind), "${cur + 1} of $n")
            Spacer(Modifier.height(18.dp))
            HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 16.dp, verticalAlignment = Alignment.Top) { page ->
                if (page < extra) HighlightCard(highlight!!, onOpen, Modifier.padding(end = 8.dp, bottom = 8.dp), today = ui.today)
                else FlipCard(cards[page - extra], Modifier.padding(end = 8.dp, bottom = 8.dp))
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InkButton("←", { scope.launch { pager.animateScrollToPage((pager.currentPage + n - 1) % n) } }, Modifier.semantics { contentDescription = "Previous fact" }, quiet = true)
                InkButton("→", { scope.launch { pager.animateScrollToPage((pager.currentPage + 1) % n) } }, Modifier.semantics { contentDescription = "Next fact" }, quiet = true)
                if (ui.all.size > 1) InkButton("Shuffle", onShuffle, quiet = true)
            }
        }
        Spacer(Modifier.height(40.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (k in ui.kinds) KindChip(KIND_LABEL.getValue(k), ui.count(k), only == k) { onOnly(k) }
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

/**
 * The featured fact: quip and figure on the face; a tap turns it over to the long version. A
 * raised stock-2 card with a hard ink shadow, like the web's `.feature-card`.
 */
@Composable
internal fun FlipCard(f: Fact, modifier: Modifier = Modifier) {
    var flipped by rememberSaveable(f.id) { mutableStateOf(false) }
    val motion = LocalMotion.current
    val turn by animateFloatAsState(if (flipped) 180f else 0f, tween(if (motion) 420 else 0), label = "flip")
    val back = turn > 90f
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().offset(6.dp, 6.dp).background(Poster.Ink))
        Column(
            Modifier.fillMaxWidth().heightIn(min = 180.dp)
                .graphicsLayer { rotationY = turn; cameraDistance = 12f * density }
                .background(if (back) Poster.Stock else Poster.Stock2).border(2.dp, Poster.Ink)
                .clickable(role = Role.Button) { flipped = !flipped }
                .semantics { contentDescription = if (back) "Fact, long version. Tap to turn back" else "Fact. Tap to turn over" }
                .padding(22.dp),
        ) {
            if (back) {
                // The back is read through a mirror; flip it once more so the type runs the right way.
                Column(Modifier.graphicsLayer { rotationY = 180f }) {
                    Text(f.headline, color = Poster.Ink, style = PosterType.Display.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 0.95.em))
                    Spacer(Modifier.height(14.dp))
                    Text(f.detail, color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 1.4.em))
                }
            } else {
                val fig = f.figure
                if (fig != null && !wide(fig)) { Box(Modifier.width(110.dp)) { Fig(fig) }; Spacer(Modifier.height(18.dp)) }
                Text((f.quip ?: f.headline).uppercase(), color = Poster.Red, style = PosterType.Display.copy(fontSize = 30.sp, lineHeight = 0.95.em, letterSpacing = (-0.01).em))
                if (fig != null && wide(fig)) { Spacer(Modifier.height(16.dp)); Fig(fig, Modifier.fillMaxWidth()) }
            }
        }
    }
}

/** One fact printed as a pass: headline, the longer line, its figure, and a stub naming its kind. */
@Composable
internal fun FactPass(f: Fact, index: Int, total: Int) {
    // Mirrors web/src/tabs/Facts: a beds figure shares as the strip, anything else as headline, detail, site.
    val text = { (f.figure as? Figure.Beds)?.let { bedText(it.nights, f.headline.uppercase()) } ?: "${f.headline.uppercase()}\n${f.detail}\n$SITE" }
    Pass(share = Share(f.headline, text), stub = { Field(KIND_LABEL.getValue(f.kind), "$index of $total") }) {
        val red = f.kind == Kind.EXPLORED || f.kind == Kind.RECORD
        Text(f.headline.uppercase(), color = if (red) Poster.Red else Poster.Ink, style = PosterType.Display.copy(fontSize = 40.sp, lineHeight = 0.9.em, letterSpacing = (-0.01).em))
        Spacer(Modifier.height(20.dp))
        Text(f.detail, color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 1.4.em))
        // Figures keep their ink text, so they print on the stock body here rather than the ink stub.
        f.figure?.let { fig ->
            Spacer(Modifier.height(28.dp))
            if (wide(fig)) Fig(fig, Modifier.fillMaxWidth()) else Box(Modifier.width(150.dp)) { Fig(fig) }
        }
    }
}
