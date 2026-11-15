package com.elsewhere.app.ui.tabs.highlights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.analytics.HFact
import com.elsewhere.app.analytics.Highlight
import com.elsewhere.app.analytics.HighlightState
import com.elsewhere.app.analytics.Scope
import com.elsewhere.app.analytics.Stop
import com.elsewhere.app.analytics.dmy
import com.elsewhere.app.analytics.highlightState
import com.elsewhere.app.analytics.localToday
import com.elsewhere.app.data.Record
import com.elsewhere.app.ui.shell.DatePickSheet
import com.elsewhere.app.ui.shell.dayOf
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.Field
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.RecordStamp
import com.elsewhere.app.ui.world.Share
import com.elsewhere.app.ui.world.TrailMap
import com.elsewhere.app.ui.world.figures.Beds
import com.elsewhere.app.ui.world.figures.LocalLand
import com.elsewhere.app.ui.world.trailLayout
import java.time.LocalDate

// Twin of web/src/tabs/highlights/HighlightCard.tsx.

private const val DAY = 86400L

/** A link printed in running text. */
@Composable
internal fun LinkText(text: String, onClick: () -> Unit) {
    Text(text, Modifier.clickable(role = Role.Button, onClick = onClick), color = Poster.Red, style = PosterType.Lede.copy(fontSize = 15.sp, textDecoration = TextDecoration.Underline))
}

@Composable
private fun Note(text: String) = Text(text, color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 15.sp))

/** The record cut to one range, for a map scoped to the day (the web's `TripMap range=`). */
internal fun dayLayout(data: Record, range: LongRange, land: com.elsewhere.app.data.FeatureCollection?) = trailLayout(
    data.copy(
        strokes = data.strokes.filter { it.end >= range.first && it.start <= range.last },
        activities = data.activities.filter { it.end >= range.first && it.start <= range.last },
    ),
    stopsIn(data, range), land, height = 260f,
)

/** One pin per city visited in the range, in order of first arrival. */
internal fun stopsIn(data: Record, range: LongRange): List<Stop> {
    val out = LinkedHashMap<String, Stop>()
    for (v in data.visits) {
        if (v.lvl != 0 || v.end < range.first || v.start > range.last) continue
        val p = data.places[v.placeId]
        val city = p?.city ?: continue
        val s = out[city]
        if (s == null) out[city] = Stop(city, p.countryCode, 0, 1, v.start, v.end, v.lat, v.lng) else { s.visits++; s.last = maxOf(s.last, v.end) }
    }
    return out.values.toList()
}

/** One day's card: headline, three facts, a scoped map, and the "Ask for more" sheet. */
@Composable
internal fun Card(data: Record, h: Highlight, today: String, onOpen: OpenDoor, modifier: Modifier = Modifier, lede: String? = null, onKeyStep: (() -> Unit)? = null, picker: @Composable () -> Unit = {}) {
    var asking by rememberSaveable(h.date) { mutableStateOf(false) }
    val land = LocalLand.current
    val layout = remember(data, h.range, land) { dayLayout(data, h.range, land) }
    val open = { onOpen(h.range, h.playhead, h.placeIds) }
    Pass(
        modifier, share = Share("highlight") { headline(h).uppercase() + "\n" + h.facts.joinToString(" · ") { it.label } },
        stub = {
            Field(if (h.scope == Scope.WEEK) "This week" else "On this day", dmy(h.date))
            Field("Years ago", if (h.yearsAgo > 0) "${h.yearsAgo}" else "—")
            picker()
            RecordStamp("Highlight", "${dayOf(h.range.first).year}", ago(h.yearsAgo), size = 92.dp)
        },
    ) {
        if (lede != null) { Note(lede); Spacer(Modifier.height(12.dp)) }
        Text(headline(h), style = PosterType.Lede.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 1.15.em))
        if (!h.cityKnown) {
            Spacer(Modifier.height(10.dp))
            Note("That place has no name yet.")
            if (onKeyStep != null) LinkText("Add a Google Maps key", onKeyStep) else Note("Add a Google Maps key to name it.")
            if (onKeyStep != null) Note("to name it.")
        }
        if (h.facts.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Column(Modifier.semantics { contentDescription = "Facts" }) { for (f in h.facts) FactRow(f, onOpen) }
        }
        Spacer(Modifier.height(16.dp))
        TrailMap(layout, Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = open).semantics { contentDescription = "Open this day on the map" }, height = 260f)
        Spacer(Modifier.height(16.dp))
        InkButton("Ask for more", { asking = true })
        AskMoreSheet(data, h, today, asking, onClose = { asking = false }, onOpen = onOpen)
    }
}

@Composable
private fun FactRow(f: HFact, onOpen: OpenDoor) {
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(f.range, f.playhead, f.placeIds) }
            .semantics { contentDescription = "${f.label} — open on the map" }.padding(vertical = 8.dp),
    ) {
        CapsText(f.label, color = Poster.Ink)
        if (f.strip != null) { Spacer(Modifier.height(8.dp)); Beds(f.strip, Modifier.width(140.dp), maxHeight = 36.dp) }
    }
}

/** "On this day": what the record has to say about today's date, with its empty states. */
@Composable
fun HighlightCard(data: Record, onOpen: OpenDoor, modifier: Modifier = Modifier, today: String? = null, onKeyStep: (() -> Unit)? = null) {
    val now = today ?: localToday()
    var date by rememberSaveable { mutableStateOf(now) }
    var showNearest by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val state = remember(data, date) { highlightState(data, date) }
    val picker: @Composable () -> Unit = {
        Column(Modifier.clickable(role = Role.Button) { picking = true }) {
            CapsText("Another day", size = 11.sp)
            Spacer(Modifier.height(6.dp))
            Text(dmy(date), style = PosterType.Lede.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.2.em))
        }
        if (picking) DatePickSheet(
            "Another", LocalDate.parse(date), dayOf(data.meta.range.first), LocalDate.parse(now), LocalDate.parse(date),
            onPick = { date = it.toString(); showNearest = false }, onDismiss = { picking = false },
        )
    }
    when (state) {
        HighlightState.None -> Unit
        is HighlightState.Short -> Card(
            data, state.highlight, now, onOpen, modifier, onKeyStep = onKeyStep, picker = picker,
            lede = "Your record starts ${dayMonth(state.starts)}. Highlights begin on ${dayMonth(state.starts)} next year — meanwhile, here's your farthest day so far.",
        )
        is HighlightState.Quiet -> {
            val near = state.nearest
            if (showNearest && near != null) Card(data, near, now, onOpen, modifier, onKeyStep = onKeyStep, picker = picker, lede = "Nothing from a ${dayMonth(date)} yet — the nearest day with a story:")
            else Pass(modifier, stub = { Field("On this day", dmy(date)); picker() }) {
                Text("Nothing from a ${dayMonth(date)} yet.", style = PosterType.Lede.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 1.15.em))
                Spacer(Modifier.height(12.dp))
                if (near != null) { Note("Nearest:"); LinkText("${dmy(near.date)}, ${near.city}", onClick = { showNearest = true }) }
                else Note("Pick another day to see what the record has to say.")
            }
        }
        is HighlightState.Found -> Card(data, state.highlight, now, onOpen, modifier, onKeyStep = onKeyStep, picker = picker)
    }
}
