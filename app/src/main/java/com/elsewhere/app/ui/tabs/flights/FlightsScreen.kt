package com.elsewhere.app.ui.tabs.flights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.analytics.Port
import com.elsewhere.app.ui.world.Bill
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DateRow
import com.elsewhere.app.ui.world.DatesList
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Field
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.LocalMuted
import com.elsewhere.app.ui.world.PList
import com.elsewhere.app.ui.world.PRow
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.Rule
import com.elsewhere.app.ui.world.Share
import com.elsewhere.app.ui.world.Tone
import com.elsewhere.app.ui.world.countUp
import com.elsewhere.app.ui.world.cv
import com.elsewhere.app.ui.world.n0
import com.elsewhere.app.ui.world.plural
import com.elsewhere.app.ui.world.u

// Port of web/src/tabs/Flights/Flights.tsx: the earth, then the ledger of routes and airports.

@Composable
fun FlightsTab(vm: FlightsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val pick by vm.pick.collectAsStateWithLifecycle()
    val u = ui
    if (u == null) { Box(Modifier.fillMaxSize().background(Poster.Stock)); return }
    // A new window reorders the routes, so the pick resets with it, as the web does.
    LaunchedEffect(u.byN) { vm.selected.value = null }
    var rotation by rememberSaveable(u.centre, stateSaver = RotationSaver) { mutableStateOf(u.centre) }
    FlightsScreen(u, rotation, { rotation = it }, selected, vm::select, pick, vm::pick)
}

@Composable
fun FlightsScreen(
    ui: FlightsUi, rotation: Rotation, onRotate: (Rotation) -> Unit, selected: Int?, onSelect: (Int?) -> Unit,
    pick: Int = 0, onPick: (Int) -> Unit = {},
    modifier: Modifier = Modifier, scroll: Boolean = true,
) {
    Column(modifier.fillMaxWidth().background(Poster.Stock).then(if (scroll) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier)) {
        if (ui.hops.isEmpty()) {
            Bill(first = true) {
                DisplayText("No flights", 48.sp)
                Spacer(Modifier.height(12.dp))
                Text("Nothing over 80 km in the air on record.", style = PosterType.Lede)
            }
            return@Column
        }
        HeroBill(ui, rotation, onRotate, selected, onSelect)
        FromBill(ui, pick, onPick)
        RoutesBill(ui, selected, onSelect)
        AirportsBill(ui)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroBill(ui: FlightsUi, rotation: Rotation, onRotate: (Rotation) -> Unit, selected: Int?, onSelect: (Int?) -> Unit) {
    val u = ui.units
    Bill(tone = Tone.INK, first = true) {
        Row { DisplayText("Up", 72.sp, color = Poster.Stock, misreg = true); Spacer(Modifier.width(8.dp)); DisplayText("There", 72.sp, color = Poster.Red, misreg = true) }
        Spacer(Modifier.height(14.dp))
        val longest = ui.longest?.let { "the longest ${n0(u.cv(it.km))} ${u.u}, ${ui.name(it.s)} to ${ui.name(it.t)}" }
        Text(
            "${countUp(ui.legs.toDouble())} ${plural(ui.legs, "flight")} on ${ui.hops.size} ${plural(ui.hops.size, "route")}. " +
                "${countUp(u.cv(ui.km))} ${u.u} above the ground${longest?.let { "; $it" } ?: ""}.",
            style = PosterType.Lede.copy(fontSize = 17.sp, lineHeight = 1.4.em),
        )
        Spacer(Modifier.height(24.dp))
        Globe(ui.byN, ui.ports, ui.land, rotation, onRotate, selected, onSelect)
        Spacer(Modifier.height(10.dp))
        CapsText("Drag to turn · tap a route", color = LocalMuted.current, size = 10.sp)
        Spacer(Modifier.height(24.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Field("Flights", countUp(ui.legs.toDouble()), big = true)
            Field("Routes", countUp(ui.hops.size.toDouble()), big = true)
            Field("Airports", countUp(ui.ports.size.toDouble()), big = true)
            Field(u.u, countUp(u.cv(ui.km)), big = true)
        }
        Spacer(Modifier.height(24.dp))
        Foot("${ui.ports.size} ${plural(ui.ports.size, "airport")} · ${ui.hops.size} ${plural(ui.hops.size, "route")}", "${n0(u.cv(ui.km))} ${u.u}")
    }
}

/** The web's `.foot`: two quiet captions, one each end of the bill. */
@Composable
private fun Foot(left: String, right: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        CapsText(left, size = 11.sp); CapsText(right, size = 11.sp)
    }
}

/**
 * One airport at a time: its routes as a ledger, the airports they reach on a globe turned to face
 * them, and a picker to change airport. Busiest first; the pick resets with the range.
 */
@Composable
internal fun FromBill(ui: FlightsUi, pick: Int, onPick: (Int) -> Unit) {
    val f = ui.from(pick) ?: return
    val u = ui.units
    val name = f.from.name
    Bill {
        // The web sets the name red beside "From", and drops it a line (`long`) past eight letters.
        if (name.length > 8) {
            DisplayText("From", 48.sp)
            DisplayText(name, 40.sp, color = Poster.Red, maxLines = 2)
        } else {
            Row(verticalAlignment = Alignment.Bottom) { DisplayText("From", 48.sp); Spacer(Modifier.width(12.dp)); DisplayText(name, 48.sp, color = Poster.Red) }
        }
        Spacer(Modifier.height(18.dp))
        AirportPicker(ui.named, pick, onPick)
        Spacer(Modifier.height(18.dp))
        DatesList(
            f.shown.map { d -> DateRow(d.name, "${n0(u.cv(d.km))} ${u.u}", "${d.n}", plural(d.n, "flight")) },
            Modifier.semantics { contentDescription = "Routes from $name" },
            more = if (f.more > 0) ({ CapsText("+ ${f.more} more", color = Poster.Red, size = 12.sp) }) else null,
        )
        Spacer(Modifier.height(24.dp))
        val centre = remember(f) { bestCentre(f.ports) }
        Globe(f.hops, f.ports, ui.land, centre, onRotate = {}, selected = null, onSelect = {})
        Spacer(Modifier.height(24.dp))
        Foot("$name · ${f.legs} ${plural(f.legs, "flight")} · ${f.hops.size} ${plural(f.hops.size, "route")}", "${n0(u.cv(f.km))} ${u.u}")
    }
}

/** The web's `<select>` of airports: a quiet ink button that opens a ruled menu, "<town> · n legs" a row. */
@Composable
private fun AirportPicker(named: List<Port>, pick: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val cur = named.getOrNull(pick.coerceIn(0, (named.size - 1).coerceAtLeast(0))) ?: return
    Box {
        InkButton("${cur.name} · ${cur.n} ${plural(cur.n, "leg")}  ▾", { open = true }, Modifier.semantics { contentDescription = "Airport" }, quiet = true)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Poster.Stock, border = BorderStroke(2.dp, Poster.Ink), shape = RectangleShape) {
            named.forEachIndexed { i, p ->
                DropdownMenuItem(
                    text = { Text("${p.name} · ${p.n} ${plural(p.n, "leg")}".uppercase(), color = if (i == pick) Poster.Red else Poster.Ink, style = PosterType.Caps.copy(fontSize = 11.sp)) },
                    onClick = { open = false; onPick(i) },
                )
            }
        }
    }
}

/** Every route, most flown first; the open one is set on mustard, and tapping a row opens or closes it. */
@Composable
internal fun RoutesBill(ui: FlightsUi, selected: Int?, onSelect: (Int?) -> Unit) {
    val u = ui.units
    Bill(share = Share("routes")) {
        DisplayText("Routes", 48.sp)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().semantics { contentDescription = "Most flown routes" }) {
            Box(Modifier.fillMaxWidth().height(Rule).background(Poster.Ink))
            ui.byN.forEachIndexed { i, h ->
                val on = i == selected
                Row(
                    Modifier.fillMaxWidth().background(if (on) Poster.Mustard else Poster.Stock)
                        .selectable(selected = on, role = Role.RadioButton) { onSelect(i) }
                        .padding(vertical = 14.dp, horizontal = if (on) 8.dp else 0.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("${i + 1}", Modifier.width(36.dp).padding(top = 6.dp), color = if (on) Poster.Ink else Poster.Red, style = PosterType.Display.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold))
                    Column(Modifier.weight(1f)) {
                        Text("${ui.name(h.s)} ↔ ${ui.name(h.t)}".uppercase(), style = PosterType.Display.copy(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.em), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${n0(u.cv(h.km))} ${u.u}", Modifier.padding(top = 4.dp), color = if (on) Poster.Ink else LocalMuted.current, style = PosterType.Label.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 1.3.em))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${h.n}", style = PosterType.Display.copy(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.em))
                        CapsText(plural(h.n, "flight"), color = if (on) Poster.Ink else LocalMuted.current, size = 10.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(LocalContentColor.current))
            }
        }
        Spacer(Modifier.height(24.dp))
        Foot("${ui.ports.size} ${plural(ui.ports.size, "airport")} · ${ui.hops.size} ${plural(ui.hops.size, "route")}", "${n0(u.cv(ui.km))} ${u.u}")
    }
}

@Composable
internal fun AirportsBill(ui: FlightsUi) {
    Bill(share = Share("airports")) {
        DisplayText("Airports", 48.sp)
        Spacer(Modifier.height(18.dp))
        PList(ui.ports.take(10).map { p -> PRow(p.name.ifEmpty { "Somewhere" }, null, "${p.n} ${plural(p.n, "leg")}") }, Modifier.semantics { contentDescription = "Busiest airports" })
        if (ui.ports.size > 10) CapsText("and ${ui.ports.size - 10} more", Modifier.padding(top = 18.dp), color = Poster.Red, size = 12.sp)
    }
}

private val RotationSaver = listSaver<Rotation, Double>(save = { listOf(it.lng, it.lat) }, restore = { Rotation(it[0], it[1]) })
