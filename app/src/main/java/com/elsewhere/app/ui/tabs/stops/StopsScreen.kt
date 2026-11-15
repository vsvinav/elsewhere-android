package com.elsewhere.app.ui.tabs.stops

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
import com.elsewhere.app.analytics.Cat
import com.elsewhere.app.ui.world.Barcode
import com.elsewhere.app.ui.world.Field
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.LocalMuted
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.PlacePhoto
import com.elsewhere.app.ui.world.PlaceSticker
import com.elsewhere.app.ui.photos.PhotoStrip
import com.elsewhere.app.ui.photos.SnapButtons
import com.elsewhere.app.ui.photos.SnapPlace
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.countUp
import com.elsewhere.app.ui.world.date
import com.elsewhere.app.ui.world.hours
import com.elsewhere.app.ui.world.n0
import com.elsewhere.app.ui.world.onFirstVisible
import kotlinx.coroutines.launch

// Port of web/src/tabs/Places/Places.tsx: a luggage tag per place, filters up top, one open at a time.

private const val PAGE = 48

@Composable
fun StopsTab(vm: StopsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    ui?.let { StopsScreen(it, vm::setFilter, onSeen = vm::seen) } ?: Box(Modifier.fillMaxSize().background(Poster.Stock))
}

@Composable
fun StopsScreen(ui: StopsUi, onFilter: (StopsFilter) -> Unit, modifier: Modifier = Modifier, scroll: Boolean = true, onSeen: (String) -> Unit = {}) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var limit by rememberSaveable(ui.filter) { mutableIntStateOf(PAGE) }
    val sel = open?.let { id -> ui.list.firstOrNull { it.id == id } }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val shown = ui.list.take(limit)
    val header: @Composable () -> Unit = {
        if (sel != null) StopDetail(sel) { open = null } else StopsHeader(ui, onFilter)
    }
    if (!scroll) {
        // Snapshot mode: lay everything out in a plain column so the sheet has a finite height.
        Column(modifier.fillMaxWidth().background(Poster.Stock).padding(12.dp)) {
            header()
            Spacer(Modifier.height(20.dp))
            shown.chunked(2).forEachIndexed { r, pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    pair.forEachIndexed { c, s -> Tag(s, r * 2 + c, open == s.id, Modifier.weight(1f).seen(s, onSeen)) { open = s.id } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        return
    }
    LazyVerticalGrid(
        GridCells.Fixed(2), modifier.fillMaxSize().background(Poster.Stock), grid,
        contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(2) }) { Column { header(); Spacer(Modifier.height(4.dp)) } }
        itemsIndexed(shown, key = { _, s -> s.id }) { i, s -> Tag(s, i, open == s.id, Modifier.seen(s, onSeen)) { open = s.id; scope.launch { grid.animateScrollToItem(0) } } }
        if (limit < ui.list.size) item(span = { GridItemSpan(2) }) {
            val more = minOf(PAGE, ui.list.size - limit)
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { InkButton("Print $more more tags", { limit += PAGE }) }
        }
    }
}

/** A tag still missing its name or photo asks for them the first time it is on screen (the web's `useInView`). */
private fun Modifier.seen(s: Stop, onSeen: (String) -> Unit) = if (s.place.name == null || s.place.photo == null) onFirstVisible { onSeen(s.id) } else this

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StopsHeader(ui: StopsUi, onFilter: (StopsFilter) -> Unit) {
    val f = ui.filter
    Pass(stub = {
        Field("Places", countUp(ui.total.toDouble()), big = true)
        Field("Named", countUp(ui.named.toDouble()), big = true)
        Field("Cities", countUp(ui.cities.toDouble()), big = true)
    }) {
        Field("Luggage tags", "One tag per place. Names come from Google Places where available.")
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(countUp(ui.list.size.toDouble()), color = Poster.Ink, style = PosterType.Display.copy(fontSize = 96.sp, fontWeight = FontWeight.Black, lineHeight = 0.85.em))
            Spacer(Modifier.width(10.dp))
            Text((f.cat?.label ?: "Places").uppercase(), color = Poster.Ink3, style = PosterType.Caps.copy(fontSize = 13.sp), modifier = Modifier.padding(bottom = 10.dp))
        }
        Spacer(Modifier.height(22.dp))
        // Category chips stand in for the web's city dropdown: the same job, a thumb-friendly shape.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in Cat.entries) {
                val n = ui.catCounts[c] ?: 0
                if (n > 0) Chip(c.label, "$n", f.cat == c, swatch = Color(android.graphics.Color.parseColor(c.color))) { onFilter(f.copy(cat = if (f.cat == c) null else c)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in Sort.entries) Chip(s.label, null, f.sort == s, role = Role.RadioButton) { onFilter(f.copy(sort = s)) }
            Chip("Named only", null, f.namedOnly) { onFilter(f.copy(namedOnly = !f.namedOnly)) }
            Text("${n0(ui.list.size)} TAGS", color = Poster.Ink3, style = PosterType.Caps.copy(fontSize = 12.sp), modifier = Modifier.padding(start = 6.dp, top = 8.dp))
        }
    }
}

@Composable
private fun Chip(label: String, n: String?, on: Boolean, role: Role = Role.Checkbox, swatch: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier.background(if (on) Poster.Ink else Poster.Stock).border(2.dp, Poster.Ink)
            .then(if (role == Role.RadioButton) Modifier.selectable(selected = on, role = role, onClick = onClick) else Modifier.toggleable(value = on, role = role, onValueChange = { onClick() }))
            .semantics { contentDescription = label }
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = PosterType.Display.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.03.em, lineHeight = 1.em)
        if (swatch != null) { Box(Modifier.size(9.dp).background(swatch).border(1.dp, if (on) Poster.Stock else Poster.Ink)); Spacer(Modifier.width(6.dp)) }
        Text(label.uppercase(), color = if (on) Poster.Stock else Poster.Ink, style = style, softWrap = false)
        if (n != null) { Spacer(Modifier.width(6.dp)); Text(n, color = if (on) Poster.Mustard else Poster.Red, style = style) }
    }
}

/** One luggage tag: photo, what and where, three quick figures, a strip of tape across the top. */
@Composable
internal fun Tag(s: Stop, index: Int, open: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tilt = listOf(-1.2f, 0.9f, -0.6f, 1.4f)[index % 4]
    Box(modifier.rotate(tilt)) {
        Column(
            Modifier.fillMaxWidth().background(if (open) Poster.Stock2 else Color.White).border(2.dp, Poster.Ink)
                .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "Open ${s.name}" }
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 14.dp),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides Poster.Ink, LocalMuted provides Poster.Ink3) {
                PlacePhoto(s.place, blank = true)
                Spacer(Modifier.height(10.dp))
                Text((s.kind ?: if (s.place.name != null) "place" else "unnamed stop").uppercase(), color = Poster.Ink3, style = PosterType.Caps.copy(fontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(s.name.uppercase(), color = Poster.Ink, style = PosterType.Display.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 0.95.em), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(s.sub, color = Poster.Ink3, style = PosterType.Label.copy(fontSize = 12.sp, lineHeight = 1.3.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Mini("Visits", n0(s.place.nVisits))
                    Mini("Time", hours(s.place.totalHours))
                    Mini("Last", date(s.place.lastTs).take(6))
                }
            }
        }
        // The tape: a strip of mustard (ghosted when the place was never named).
        Box(Modifier.align(Alignment.TopCenter).width(56.dp).height(14.dp).rotate(-3f).background(if (s.place.name != null) Poster.Mustard.copy(alpha = 0.9f) else Poster.Ink.copy(alpha = 0.12f)))
    }
}

@Composable
private fun Mini(label: String, value: String) {
    Column {
        Text(label.uppercase(), color = Poster.Ink3, style = PosterType.Caps.copy(fontSize = 9.sp))
        Text(value, color = Poster.Ink, style = PosterType.Display.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.em), softWrap = false)
    }
}

/** The opened tag: the place at full size with its figures on the stub. No map on this sheet. */
@Composable
internal fun StopDetail(s: Stop, onBack: () -> Unit) {
    val p = s.place
    Pass(stub = {
        Field("Visits", countUp(p.nVisits.toDouble()), big = true)
        Field("Time there", hours(p.totalHours), big = true)
        Field("First seen", date(p.firstTs))
        Field("Last seen", date(p.lastTs))
        Barcode("PLC ${s.id.take(12).uppercase()} ${p.nVisits}V")
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Field("Place", s.kind ?: "unnamed stop", Modifier.weight(1f))
            Field("City", p.city, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Field("Address", p.address)
        Spacer(Modifier.height(20.dp))
        Text(s.name.uppercase(), color = Poster.Ink, style = PosterType.Display.copy(fontSize = 56.sp, fontWeight = FontWeight.Black, lineHeight = 0.9.em))
        Spacer(Modifier.height(28.dp))
        PlaceSticker(p, s.name, s.sub, Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally), tilt = 1.5f)
        Spacer(Modifier.height(28.dp))
        val snapPlace = remember(s) { SnapPlace(s.id, s.name, p.lat, p.lng, p.city, p.address) }
        PhotoStrip(snapPlace, Modifier.padding(bottom = 20.dp))
        SnapButtons(snapPlace)
        Spacer(Modifier.height(12.dp))
        InkButton("Back to all places", onBack, quiet = true)
    }
}
