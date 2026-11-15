package com.elsewhere.app.ui.tabs.highlights

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.analytics.Answer
import com.elsewhere.app.analytics.Door
import com.elsewhere.app.analytics.FollowUp
import com.elsewhere.app.analytics.FollowUpId
import com.elsewhere.app.analytics.Highlight
import com.elsewhere.app.analytics.MAX_DEPTH
import com.elsewhere.app.analytics.Scope
import com.elsewhere.app.analytics.dmy
import com.elsewhere.app.analytics.followUps
import com.elsewhere.app.analytics.localDay
import com.elsewhere.app.data.Record
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.TrailMap
import com.elsewhere.app.ui.world.figures.Beds
import com.elsewhere.app.ui.world.figures.LocalLand
import com.elsewhere.app.ui.world.n0
import java.util.Locale

// Twin of web/src/tabs/highlights/AskMoreDrawer.tsx, as a ModalBottomSheet.

private val CRUMB = mapOf(
    FollowUpId.STAY to "the stay", FollowUpId.DISTANCE to "the distance", FollowUpId.TRIP to "the trip", FollowUpId.ROUTE to "the route",
    FollowUpId.COMPARE to "compare years", FollowUpId.DID to "the day", FollowUpId.BACK to "been back", FollowUpId.FARTHEST to "farthest", FollowUpId.LAST_YEAR to "last year",
)

internal data class Step(val fu: FollowUp, val h: Highlight)

private val Step.asHighlight: Highlight? get() = (fu.answer as? Answer.HighlightAnswer)?.highlight

@Composable
private fun Title(text: String) = Text(text, style = PosterType.Lede.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 1.15.em))

@Composable
private fun Detail(text: String) = Text(text, color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 15.sp))

@Composable
private fun DoorRow(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

private fun hoursText(h: Double) = if (h < 1) "${Math.round(h * 60)} min" else String.format(Locale.US, if (h < 10) "%.1f h" else "%.0f h", h)

/** One answer printed as a pass: title, detail, the figure for its kind, and a door to the map. */
@Composable
internal fun AnswerPass(data: Record, step: Step, onOpen: OpenDoor) {
    val a = step.fu.answer
    val door: (Door) -> () -> Unit = { d -> { onOpen(d.range, d.playhead, d.placeIds) } }
    Pass {
        CapsText(step.fu.question)
        Spacer(Modifier.height(10.dp))
        when (a) {
            is Answer.HighlightAnswer -> {
                Title(headline(a.highlight))
                Detail(dmy(a.highlight.date))
                if (a.highlight.facts.isNotEmpty()) { Spacer(Modifier.height(8.dp)); for (f in a.highlight.facts) DoorRow(door(f)) { CapsText(f.label, color = Poster.Ink, size = 11.sp) } }
            }
            else -> { Title(a.title); Detail(a.detail) }
        }
        when (a) {
            is Answer.Stay -> { Spacer(Modifier.height(12.dp)); Beds(a.strip, Modifier.width(200.dp), maxHeight = 48.dp) }
            is Answer.TripSetlist -> { Spacer(Modifier.height(8.dp)); for (c in a.cities) DoorRow(door(c)) {
                Text(c.city, style = PosterType.Lede.copy(fontWeight = FontWeight.Bold))
                Detail("${c.nights} ${if (c.nights == 1) "night" else "nights"}")
                if (c.first) CapsText("first time", color = Poster.Red, size = 11.sp)
            } }
            is Answer.Route -> { Spacer(Modifier.height(12.dp)); RouteFigure(data, a, step.h.tz) }
            is Answer.Compare -> {
                val max = maxOf(1.0, a.years.maxOf { it.km ?: 0.0 })
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = "Years" }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    for (y in a.years) Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                            if (y.km != null) Box(Modifier.fillMaxWidth().fillMaxHeight(maxOf(0.04f, (y.km / max).toFloat())).background(if (y.now) Poster.Red else Poster.Ink))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("${y.year}", style = PosterType.Label.copy(fontWeight = FontWeight.Bold))
                        Text(y.city ?: "—", style = PosterType.Label, maxLines = 1)
                        if (y.km != null) Text("${n0(y.km)} km", color = Poster.Ink2, style = PosterType.Label)
                        if (y.now) CapsText("now", color = Poster.Red, size = 10.sp)
                    }
                }
            }
            is Answer.Did -> { Spacer(Modifier.height(8.dp)); for (s in a.stops) DoorRow(door(s)) {
                Text(s.name, Modifier.weight(1f), style = PosterType.Lede.copy(fontWeight = FontWeight.Bold))
                if (s.type != null) Detail(s.type.replace('_', ' '))
                Detail(hoursText(s.hours))
            } }
            else -> Unit
        }
        Spacer(Modifier.height(14.dp))
        val label = when {
            a is Answer.Farthest && !a.isFarthest -> "Open ${a.city ?: dmy(a.date)}"
            a is Answer.Back && a.last != null -> "Open the last visit"
            else -> "Open on the map"
        }
        InkButton(label, door(a), quiet = true)
    }
}

/** The route on its own map with a ten-second replay of the trip; the door to the real map stays. */
@Composable
internal fun RouteFigure(data: Record, a: Answer.Route, tz: Int, replay: ReplayState = rememberReplay(a.range)) {
    val land = LocalLand.current
    val stops = remember(data, a.range) { stopsIn(data, a.range) }
    val g = remember(data, a.range) { routeGeometry(data, a.range) }
    val fit = remember(g, stops, land) { routeFit(data, g, stops, land, 220f) }
    val playhead = replay.playhead
    val layout = remember(g, fit, playhead) { if (playhead == null) fit else routeLayout(data, routeAt(g, playhead), stops, land, fit, 220f) }
    TrailMap(layout, Modifier.fillMaxWidth().semantics { contentDescription = "Route map" }, height = 220f)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        InkButton(
            when { replay.playing -> "Replaying…"; playhead == null -> "Play this route"; else -> "Play again" },
            replay::play, Modifier.semantics { selected = replay.playing; stateDescription = if (replay.playing) "Replaying" else "Stopped" },
        )
        if (playhead != null) CapsText(dmy(localDay(playhead, tz)), Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Playhead" }, size = 11.sp)
    }
}

/** The sheet's contents, stateful over the thread; snapshot-tested without the sheet. */
@Composable
internal fun AskMoreContent(data: Record, highlight: Highlight, today: String, onClose: () -> Unit, onOpen: OpenDoor, modifier: Modifier = Modifier, initial: List<Step> = emptyList()) {
    var chain by remember(highlight) { mutableStateOf(initial) }
    val cur = chain.lastOrNull()?.h ?: highlight
    // Questions already asked since the thread last moved to another day.
    val asked = remember(chain) { val out = LinkedHashSet<FollowUpId>(); for (s in chain) { if (s.asHighlight != null) out.clear() else out.add(s.fu.id) }; out as Set<FollowUpId> }
    val menu = remember(data, cur, chain.size, asked, today) { followUps(data, cur, chain.size, asked, today) }
    val crumbs = listOf(highlight.city) + chain.map { s -> s.asHighlight?.city ?: CRUMB.getValue(s.fu.id) }
    // Back pops one answer off the thread; on the highlight itself it closes the sheet.
    BackHandler { if (chain.isNotEmpty()) chain = chain.dropLast(1) else onClose() }

    Column(modifier.fillMaxWidth().background(Poster.Stock).semantics { contentDescription = "Ask for more" }) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).semantics { contentDescription = "Thread" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                crumbs.forEachIndexed { i, c -> if (i > 0) CapsText("›", size = 11.sp); CapsText(c, color = if (i == crumbs.lastIndex) Poster.Ink else Poster.Ink3, size = 11.sp) }
            }
            Text("×", Modifier.clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = "Close" }.padding(12.dp), style = PosterType.Lede.copy(fontSize = 22.sp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text(headline(cur), style = PosterType.Lede.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
                Detail("${dmy(cur.date)}${if (cur.scope == Scope.WEEK) " · this week" else ""}, ${ago(cur.yearsAgo)}")
            }
            for (s in chain) AnswerPass(data, s, onOpen)
            if (menu.isNotEmpty()) Column(Modifier.semantics { contentDescription = "Ask" }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (f in menu) Text(
                    f.question,
                    Modifier.fillMaxWidth().testTag("hl-q").clickable(role = Role.Button) { chain = chain + Step(f, (f.answer as? Answer.HighlightAnswer)?.highlight ?: cur) }.padding(vertical = 10.dp),
                    style = PosterType.Lede.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            else CapsText(if (chain.size >= MAX_DEPTH) "That's as far as this thread goes." else "Nothing more to ask about this one.", Modifier.semantics { contentDescription = "status" })
        }
    }
}

/**
 * The "Ask for more" sheet: a menu of only the questions the record can answer about the
 * highlight; each answer prints as a pass and offers its own follow-ups, down to MAX_DEPTH.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskMoreSheet(data: Record, highlight: Highlight, today: String, open: Boolean, onClose: () -> Unit, onOpen: OpenDoor) {
    if (!open) return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = Poster.Stock, contentColor = Poster.Ink, shape = RectangleShape, dragHandle = null) {
        AskMoreContent(data, highlight, today, onClose, onOpen)
    }
}
