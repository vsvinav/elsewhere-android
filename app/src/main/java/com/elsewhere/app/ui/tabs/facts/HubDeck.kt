package com.elsewhere.app.ui.tabs.facts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.analytics.Highlight
import com.elsewhere.app.analytics.HighlightState
import com.elsewhere.app.analytics.HubFigures
import com.elsewhere.app.analytics.Kind
import com.elsewhere.app.analytics.Passport
import com.elsewhere.app.analytics.YearFacts
import com.elsewhere.app.analytics.compareYears
import com.elsewhere.app.analytics.cycleYear
import com.elsewhere.app.analytics.dmy
import com.elsewhere.app.analytics.fmtSpan
import com.elsewhere.app.analytics.highlightState
import com.elsewhere.app.analytics.localToday
import com.elsewhere.app.analytics.tilt
import com.elsewhere.app.analytics.todaysFact
import com.elsewhere.app.data.Units
import com.elsewhere.app.ui.tabs.highlights.OpenDoor
import com.elsewhere.app.ui.tabs.highlights.dayMonth
import com.elsewhere.app.ui.tabs.highlights.headline
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.Field
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.LocalMuted
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.WEEKDAYS
import com.elsewhere.app.ui.world.cv
import com.elsewhere.app.ui.world.motionOn
import com.elsewhere.app.ui.world.n0
import com.elsewhere.app.ui.world.u
import kotlinx.coroutines.launch
import java.util.Locale

// Port of web/src/tabs/Facts/HubDeck.tsx: six poster cards along the top of the Facts tab, each opening its full pass.

internal val HUB_CARDS = listOf("today", "onthisday", "streaks", "passport", "compare", "rhythm")
private val CARD_W = 290.dp
private val CARD_H = 400.dp

/** "Sat" from the poster's MON..SUN. */
private fun dayName(d: Int) = WEEKDAYS[d].lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
private fun hh(h: Int) = "${h.toString().padStart(2, '0')}:00"

private enum class Tone { STOCK, INK, RED }

/** One card: kind label, body, and the OPEN row at the foot. Leans by its hash until it is the one in front. */
@Composable
private fun HubCard(id: String, kind: String, selected: Boolean, tone: Tone = Tone.STOCK, open: Pair<String, String>? = null, onOpen: () -> Unit = {}, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val bg = when (tone) { Tone.STOCK -> Poster.Stock; Tone.INK -> Poster.Ink; Tone.RED -> Poster.Red }
    val fg = if (tone == Tone.STOCK) Poster.Ink else Poster.Stock
    val accent = if (tone == Tone.STOCK) Poster.Red else Poster.Mustard
    val lean = if (selected) 0f else tilt(id) / 3f
    Box(modifier.width(CARD_W).graphicsLayer { rotationZ = lean }) {
        Box(Modifier.matchParentSize().padding(start = 6.dp, top = 6.dp).background(Poster.Ink))
        Column(
            Modifier.width(CARD_W).height(CARD_H).padding(end = 6.dp, bottom = 6.dp).background(bg).border(3.dp, Poster.Ink)
                .semantics { contentDescription = "Card: $kind"; this.selected = selected }
                .padding(18.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides fg, LocalMuted provides if (tone == Tone.STOCK) Poster.Ink3 else Poster.Stock3) {
                CapsText(kind, color = fg.copy(alpha = 0.8f), size = 10.sp)
                content()
                Spacer(Modifier.weight(1f))
                if (open != null) {
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(fg))
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).semantics { contentDescription = open.second }.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("OPEN · ${open.first.uppercase()}", color = accent, style = PosterType.Caps.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.em))
                        Text("→", color = accent, style = PosterType.Caps.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
private fun Headline(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(text.uppercase(), Modifier.semantics { heading() }, color = LocalContentColor.current, style = PosterType.Display.copy(fontSize = 38.sp, lineHeight = 0.9.em, letterSpacing = (-0.01).em), maxLines = 3, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Body(text: String) {
    Text(text, color = LocalContentColor.current, style = PosterType.Lede.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 1.35.em))
}

@Composable
private fun StreakRow(value: Int, label: String, note: String, last: Boolean = false, zero: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$value", color = if (zero) Poster.Red else LocalContentColor.current, style = PosterType.Display.copy(fontSize = 34.sp, lineHeight = 0.9.em))
        Column {
            CapsText(label, color = LocalContentColor.current, size = 10.sp)
            Spacer(Modifier.height(3.dp))
            Text(note, color = LocalContentColor.current.copy(alpha = 0.75f), style = PosterType.Lede.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 1.2.em))
        }
    }
    if (!last) Box(Modifier.fillMaxWidth().height(2.dp).background(LocalContentColor.current))
}

/** The booklet folded to one line: the stamped count and the latest few stamps. */
@Composable
internal fun PassportCompact(pass: Passport, max: Int = 3) {
    val latest = remember(pass) { pass.pages.flatMap { it.stamps }.filter { it.earned }.sortedByDescending { it.earnedAt!! }.take(max) }
    Spacer(Modifier.height(14.dp))
    Field("Stamped", "${pass.earnedIds.size} / ${pass.total}")
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        for (s in latest) PassportStamp(s, diameter = 76.dp, animate = false)
    }
}

@Composable
private fun YearChip(year: Int, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.border(2.dp, Poster.Ink).clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = "Compare year $year" }.padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text("$year", color = Poster.Ink, style = PosterType.Display.copy(fontSize = 22.sp, lineHeight = 1.em, letterSpacing = 0.04.em)) }
}

@Composable
private fun CompareRow(label: String, a: String, b: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) { contentDescription = "$label: $a against $b" }, verticalAlignment = Alignment.CenterVertically) {
        CapsText(label, Modifier.weight(1f), size = 10.sp)
        val style = PosterType.Display.copy(fontSize = 24.sp, lineHeight = 1.em)
        Text(a, Modifier.width(70.dp), color = Poster.Ink, style = style, textAlign = TextAlign.End)
        Text(b, Modifier.width(70.dp), color = Poster.Ink, style = style, textAlign = TextAlign.End)
    }
    if (!last) Box(Modifier.fillMaxWidth().height(2.dp).background(Poster.Ink))
}

/** The Numbers tab's weekday × hour grid, shrunk to the card: one light cell per hour on the red stock. */
@Composable
private fun HeatMini(grid: List<List<Double>>, max: Double) {
    Canvas(Modifier.fillMaxWidth().height(63.dp).semantics { contentDescription = "Heat grid of hours away from home by weekday and hour" }) {
        val gap = 1.dp.toPx()
        val w = (size.width - gap * 23) / 24
        val h = (size.height - gap * 6) / 7
        grid.forEachIndexed { d, row ->
            row.forEachIndexed { hr, v ->
                val a = if (max > 0) maxOf(0.12, v / max) else 0.12
                drawRect(Poster.Stock, Offset(hr * (w + gap), d * (h + gap)), Size(w, h), alpha = a.toFloat())
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HubDeck(
    ui: FactsUi, hub: HubFigures, modifier: Modifier = Modifier,
    onOpen: OpenDoor = { _, _, _ -> }, onOpenKind: (Kind) -> Unit = {}, onOpenPassport: () -> Unit = {}, page: Int = 0,
) {
    val today = ui.today ?: localToday()
    val fact = remember(ui.all, today) { todaysFact(ui.all, today) }
    val day = remember(ui.raw, today) { ui.raw?.let { highlightState(it, today) } ?: HighlightState.None }
    val highlight: Highlight? = when (day) { is HighlightState.Found -> day.highlight; is HighlightState.Short -> day.highlight; else -> null }
    val years = hub.yearList
    val latest = hub.latestPair
    var pairA by rememberSaveable(years) { mutableIntStateOf(latest?.first ?: 0) }
    var pairB by rememberSaveable(years) { mutableIntStateOf(latest?.second ?: 0) }
    val side = remember(hub, pairA, pairB) { compareYears(hub.years, pairA, pairB) }

    val n = HUB_CARDS.size
    val pager = rememberPagerState(initialPage = page, pageCount = { n })
    val scope = rememberCoroutineScope()
    val motion = motionOn()
    val go: (Int) -> Unit = { i -> scope.launch { val k = i.coerceIn(0, n - 1); pager.animateScrollToPage(k, animationSpec = if (motion) spring(stiffness = Spring.StiffnessMediumLow) else snap()) } }
    val cur = pager.currentPage.coerceIn(0, n - 1)

    Column(modifier.fillMaxWidth().semantics { contentDescription = "Play deck" }) {
        HorizontalPager(pager, Modifier.fillMaxWidth(), pageSize = PageSize.Fixed(CARD_W), pageSpacing = 12.dp, contentPadding = PaddingValues(end = 40.dp), beyondViewportPageCount = 1, verticalAlignment = Alignment.Top) { i ->
            val sel = i == cur
            when (HUB_CARDS[i]) {
                "today" -> HubCard("today", "Today", sel, open = fact?.let { "${KIND_LABEL.getValue(it.kind)} facts" to "Open ${KIND_LABEL.getValue(it.kind).lowercase()} facts" }, onOpen = { fact?.let { onOpenKind(it.kind) } }) {
                    Headline(fact?.headline ?: "Nothing yet")
                    Body(fact?.detail ?: "Widen the range, or wait for the record to fill in.")
                    Spacer(Modifier.height(12.dp))
                    CapsText(dmy(today), size = 10.sp)
                }
                "onthisday" -> HubCard("onthisday", "On this day", sel, open = highlight?.let { "The map" to "Open on the map" }, onOpen = { highlight?.let { onOpen(it.range, it.playhead, it.placeIds) } }) {
                    if (highlight != null) {
                        Headline(highlight.city)
                        Body("${dmy(highlight.date)} — you ${highlight.verb.text} ${highlight.city}.")
                        Spacer(Modifier.height(12.dp))
                        CapsText(if (day is HighlightState.Short) "Your record starts ${dayMonth(day.starts)} — meanwhile, here's your farthest day so far." else headline(highlight), size = 10.sp)
                    } else {
                        Headline("Quiet day")
                        val near = (day as? HighlightState.Quiet)?.nearest
                        Body(if (near != null) "Nothing on ${dmy(today).dropLast(5)} in earlier years. The nearest thing on record: ${dmy(near.date)}, ${near.city}." else "Nothing on this day in earlier years.")
                    }
                }
                "streaks" -> HubCard("streaks", "Streaks", sel, tone = Tone.INK, open = "Record facts" to "Open record facts", onOpen = { onOpenKind(Kind.RECORD) }) {
                    val s = hub.streaks
                    Spacer(Modifier.height(10.dp))
                    StreakRow(s.away?.n ?: 0, "Longest away streak", s.away?.let { fmtSpan(it.from, it.to) } ?: "No nights away yet")
                    StreakRow(s.newWeeks?.n ?: 0, "Weeks with a new place", s.newWeeks?.let { fmtSpan(it.from, it.to) } ?: "Nothing new yet")
                    StreakRow(s.current, "Current away streak", if (s.current > 0) "nights and counting" else "Home tonight", last = true, zero = s.current == 0)
                }
                "passport" -> HubCard("passport", "Passport", sel, open = "Passport" to "Open passport", onOpen = onOpenPassport) {
                    ui.passport?.let { PassportCompact(it) }
                }
                "compare" -> HubCard("compare", "Compare", sel, open = "Scale facts" to "Open scale facts", onOpen = { onOpenKind(Kind.SCALE) }) {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        YearChip(pairA, years.size >= 3, { pairA = cycleYear(years, pairA, pairB) }, Modifier.weight(1f))
                        YearChip(pairB, years.size >= 3, { pairB = cycleYear(years, pairB, pairA) }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    val (a, b) = side
                    val rows: List<Pair<String, (YearFacts) -> String>> = listOf("Places" to { f -> n0(f.places) }, "Nights away" to { f -> n0(f.nightsAway) }, ui.units.u to { f -> n0(ui.units.cv(f.km)) }, "Countries" to { f -> n0(f.countries) })
                    rows.forEachIndexed { k, (label, of) -> CompareRow(label, of(a), of(b), last = k == rows.lastIndex) }
                }
                "rhythm" -> HubCard("rhythm", "Rhythm", sel, tone = Tone.RED, open = "Habit facts" to "Open habit facts", onOpen = { onOpenKind(Kind.HABIT) }) {
                    val p = hub.peak
                    Headline(if (p != null) "${dayName(p.d)} ${hh(p.h)}" else "Home all week")
                    Body(if (p != null) "Busiest: ${dayName(p.d)} ${hh(p.h)}. Hours away from home, by weekday and hour." else "No hours away from home on record.")
                    Spacer(Modifier.height(14.dp))
                    HeatMini(hub.rhythm, hub.rhythmMax)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InkButton("←", { go(cur - 1) }, Modifier.semantics { contentDescription = "Previous card" }, quiet = true, enabled = cur > 0)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until n) {
                    val on = i == cur
                    Box(
                        Modifier.size(16.dp).selectable(selected = on, role = Role.Tab, onClick = { go(i) }).semantics { contentDescription = "Card ${i + 1} of $n" },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(8.dp).border(2.dp, Poster.Ink).background(if (on) Poster.Ink else Color.Transparent)) }
                }
            }
            InkButton("→", { go(cur + 1) }, Modifier.semantics { contentDescription = "Next card" }, quiet = true, enabled = cur < n - 1)
            CapsText("${cur + 1} of $n", size = 11.sp)
        }
    }
}
