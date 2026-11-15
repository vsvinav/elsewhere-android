package com.elsewhere.app.ui.tabs.dates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.analytics.placeName
import com.elsewhere.app.data.Units
import com.elsewhere.app.ui.nav.doorId
import com.elsewhere.app.ui.world.Barcode
import com.elsewhere.app.ui.world.Field
import com.elsewhere.app.ui.world.NightsBill
import com.elsewhere.app.ui.world.PList
import com.elsewhere.app.ui.world.PRow
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.Share
import com.elsewhere.app.share.bedText
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.countUp
import com.elsewhere.app.ui.world.cv
import com.elsewhere.app.ui.world.date
import com.elsewhere.app.ui.world.figures.Beds
import com.elsewhere.app.ui.world.label
import com.elsewhere.app.ui.world.n0
import com.elsewhere.app.ui.world.u
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Port of web/src/world/Nights.tsx (the bed strip) followed by web/src/tabs/Trips/Trips.tsx.

@Composable
fun DatesTab(vm: DatesViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    ui?.let { DatesScreen(it, onSeen = vm::seen) } ?: Box(Modifier.fillMaxSize().background(Poster.Stock))
}

@Composable
fun DatesScreen(ui: DatesUi, modifier: Modifier = Modifier, scroll: Boolean = true, trips: Boolean = true, onSeen: (String) -> Unit = {}) {
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(ui.cards, query) { searchCards(ui.cards, query) }
    val kept = remember(shown) { shown.mapTo(HashSet()) { it.tripId } }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    // Where each pass sits in the column, so a card can scroll its pass into view.
    val passY = remember { HashMap<String, Int>() }
    var topY by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxWidth().background(Poster.Stock).onGloballyPositioned { topY = it.positionInRoot().y.toInt() }.then(if (scroll) Modifier.fillMaxSize().verticalScroll(scrollState) else Modifier)) {
        ui.nights?.let { NightsBill(it, first = true) }
        if (trips) Column(Modifier.padding(horizontal = 12.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            DeckSearch(query, { query = it }, shown.size, ui.cards.size)
            TripDeck(shown, ui.data, ui.units, ui.land, onOpen = { c ->
                open = c.departed
                passY[c.tripId]?.let { y -> scope.launch { scrollState.animateScrollTo(y) } }
            })
            TripsHeader(ui)
            for (t in ui.trips) {
                if (t.trip.doorId !in kept) continue
                val isOpen = open == t.trip.start
                // Beds and stops are worked out only for the trip that is open, off the main thread.
                val detail by produceState<TripDetail?>(null, isOpen, ui) {
                    value = if (isOpen) withContext(Dispatchers.Default) { tripDetail(ui.data, ui.strip, t.trip) } else null
                }
                TripPass(t, ui.units, isOpen, detail, onSeen, Modifier.onGloballyPositioned { passY[t.trip.doorId] = it.positionInRoot().y.toInt() - topY + scrollState.value }) { open = if (isOpen) null else t.trip.start }
            }
        }
    }
}

@Composable
internal fun TripsHeader(ui: DatesUi) {
    val u = ui.units
    Pass(share = Share("trips"), stub = {
        Field("Trips", countUp(ui.trips.size.toDouble()), big = true)
        Field("Nights away", countUp(ui.nightsAway.toDouble()), big = true)
        Field("Flights", countUp(ui.flights.toDouble()), big = true)
    }) {
        Field("Trips", "Nights spent more than ${n0(u.cv(100.0))} ${u.u} from home, newest first")
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(countUp(ui.nightsAway.toDouble()), color = Poster.Ink, style = PosterType.Display.copy(fontSize = 96.sp, fontWeight = FontWeight.Black, lineHeight = 0.85.em))
            Spacer(Modifier.width(10.dp))
            Text("NIGHTS AWAY", color = Poster.Ink3, style = PosterType.Caps.copy(fontSize = 13.sp), modifier = Modifier.padding(bottom = 10.dp))
        }
        if (ui.route.isNotEmpty()) { Spacer(Modifier.height(18.dp)); Route(ui.route, 34.sp) }
    }
}

/** Cities joined by red arrows, the web's `.route`. */
@Composable
private fun Route(cities: List<String>, size: androidx.compose.ui.unit.TextUnit) {
    Text(
        buildAnnotatedString {
            cities.forEachIndexed { i, c ->
                if (i > 0) withStyle(SpanStyle(color = Poster.Red)) { append(" → ") }
                append(c.uppercase())
            }
        },
        color = Poster.Ink, style = PosterType.Display.copy(fontSize = size, fontWeight = FontWeight.ExtraBold, lineHeight = 1.05.em, letterSpacing = 0.01.em),
    )
}

@Composable
internal fun TripPass(t: TripUi, units: Units, open: Boolean, detail: TripDetail?, onSeen: (String) -> Unit = {}, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val trip = t.trip
    val dest = trip.anchorCities.take(3)
    // The strip is only worked out for the open trip, so only an open trip offers its text.
    val text = detail?.let { d -> { bedText(d.beds, "${dest.joinToString(" → ").ifEmpty { "TRIP" }} · ${date(trip.start)}".uppercase()) } }
    Pass(modifier, share = Share("trip ${date(trip.start)}", text), stub = {
        Field("Nights", countUp(trip.nights.toDouble()), big = true)
        Field("Flights", "${t.flights}", big = true)
        Field("Farthest", "${n0(units.cv(trip.maxDistFromHomeKm))} ${units.u.uppercase()}", big = true)
        Barcode("TRIP ${date(trip.start).replace(" ", "")} ${trip.nights}N ${n0(t.totalKm).replace(",", "")}KM")
    }) {
        Column(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle)
                .semantics { contentDescription = if (open) "Close trip" else "Open trip" },
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Field("Departed", date(trip.start), Modifier.weight(1f))
                Field("Returned", date(trip.end), Modifier.weight(1f))
                Field("Distance", "${n0(units.cv(t.totalKm))} ${units.u}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            if (dest.isNotEmpty()) Route(dest, 26.sp)
            else Text("DESTINATION NOT ON RECORD", color = Poster.Ink3, style = PosterType.Display.copy(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold))
            AnimatedVisibility(open && detail != null) {
                Column {
                    Spacer(Modifier.height(22.dp))
                    Beds(detail?.beds.orEmpty(), Modifier.fillMaxWidth(), maxHeight = 60.dp)
                    Spacer(Modifier.height(22.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val top = t.modes.firstOrNull()?.second ?: 1.0
                        t.modes.forEachIndexed { j, (m, v) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(label(m).uppercase(), Modifier.width(72.dp), color = Poster.Ink3, style = PosterType.Caps.copy(fontSize = 11.sp))
                                Box(Modifier.weight(1f).height(22.dp).background(Poster.Stock2)) {
                                    Box(Modifier.fillMaxWidth((v / top).toFloat().coerceIn(0f, 1f)).height(22.dp).background(when (j) { 0 -> Poster.Red; 1 -> Poster.Mustard; else -> Poster.Ink }))
                                }
                                Text("${n0(units.cv(v))} ${units.u}", color = Poster.Ink, style = PosterType.Display.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), softWrap = false)
                            }
                        }
                    }
                    val visited = detail?.visited.orEmpty()
                    if (visited.isNotEmpty()) {
                        Spacer(Modifier.height(22.dp))
                        PList(visited.map { PRow(placeName(it.place, it.id), it.place?.city ?: "", "${it.n}", id = it.id.takeIf { _ -> it.place?.name == null || it.place.photo == null }) }, onSeen = onSeen)
                    }
                }
            }
            if (!open) {
                Spacer(Modifier.height(18.dp))
                Text("OPEN TRIP →", color = Poster.Red, style = PosterType.Caps.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.em))
            }
        }
    }
}
