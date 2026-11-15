package com.elsewhere.app.ui.tabs.dates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.data.FeatureCollection
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.Units
import com.elsewhere.app.ui.nav.Door
import com.elsewhere.app.ui.nav.door
import com.elsewhere.app.ui.tabs.highlights.stopsIn
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.TrailLayout
import com.elsewhere.app.ui.world.TrailMap
import com.elsewhere.app.ui.world.cv
import com.elsewhere.app.ui.world.date
import com.elsewhere.app.ui.world.n0
import com.elsewhere.app.ui.world.trailLayout
import com.elsewhere.app.ui.world.u
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Port of web/src/tabs/Trips/TripDeck.tsx.

private const val THUMB_H = 300f

/** The search box above the deck: the web's `.deck-search`. */
@Composable
internal fun DeckSearch(query: String, onQuery: (String) -> Unit, shown: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(Poster.Stock).border(2.dp, Poster.Ink).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CapsText("Search", color = Poster.Ink, size = 12.sp)
        BasicTextField(
            value = query, onValueChange = onQuery, singleLine = true, cursorBrush = SolidColor(Poster.Red),
            textStyle = PosterType.Lede.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Poster.Ink),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = Modifier.weight(1f).semantics { contentDescription = "Search trips" },
            decorationBox = { inner -> Box { if (query.isEmpty()) Text("lisbon 2024", color = Poster.Ghost, style = PosterType.Lede.copy(fontSize = 18.sp)); inner() } },
        )
        CapsText(if (shown == total) "$total TRIPS" else "$shown OF $total", size = 11.sp)
    }
}

/** The record cut to one trip on a small sheet: the card's trail thumbnail. */
fun tripThumb(data: Record, card: Card, land: FeatureCollection?): TrailLayout {
    val range = card.departed..card.returned
    return trailLayout(
        data.copy(
            strokes = data.strokes.filter { it.end >= range.first && it.start <= range.last },
            activities = data.activities.filter { it.end >= range.first && it.start <= range.last },
        ),
        stopsIn(data, range), land, height = THUMB_H,
    )
}

/** The trip's own route, laid out off the main thread once the card is near the viewport; `ready` skips the wait. */
@Composable
private fun TripThumb(data: Record, card: Card, land: FeatureCollection?, ready: TrailLayout?) {
    val layout by produceState(ready, data, card, land) {
        if (value == null) value = withContext(Dispatchers.Default) { tripThumb(data, card, land) }
    }
    Box(Modifier.fillMaxWidth().height(100.dp).background(Poster.Stock2), contentAlignment = Alignment.Center) {
        layout?.let { TrailMap(it, Modifier.semantics { contentDescription = "Map of this trip" }, height = THUMB_H) }
    }
}

@Composable
private fun Stat(label: String, value: String, win: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CapsText(label, size = 10.sp)
            if (win) Box(Modifier.size(13.dp).background(Poster.Mustard).semantics { contentDescription = "Beats the trip before" }, contentAlignment = Alignment.Center) {
                Text("✓", color = Poster.Ink, style = PosterType.Caps.copy(fontSize = 9.sp, letterSpacing = 0.sp, lineHeight = 1.em))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(value.uppercase(), color = Poster.Ink, style = PosterType.Display.copy(fontSize = 30.sp, lineHeight = 0.9.em), maxLines = 1)
    }
}

/** One card of the deck; `prev` is the older card beside it, whose stats this one is ticked against. */
@Composable
internal fun TripCard(card: Card, prev: Card?, data: Record, units: Units, land: FeatureCollection?, selected: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier, thumb: TrailLayout? = null) {
    val won = prev?.let { compare(card, it) }
    val label = "${card.title} · ${card.nights} night${if (card.nights == 1) "" else "s"}"
    Column(
        modifier.fillMaxWidth().background(Poster.Stock).border(2.dp, Poster.Ink)
            .semantics { contentDescription = "Trip card: ${card.title}"; this.selected = selected },
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
            Row { CapsText(date(card.departed), size = 10.sp); CapsText(" → ", color = Poster.Red, size = 10.sp); CapsText(date(card.returned), size = 10.sp) }
            Spacer(Modifier.height(6.dp))
            Text(card.title.uppercase(), color = Poster.Ink, style = PosterType.Display.copy(fontSize = 32.sp, lineHeight = 0.9.em), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Poster.Ink))
        TripThumb(data, card, land, thumb)
        Box(Modifier.fillMaxWidth().height(2.dp).background(Poster.Ink))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Nights", "${card.nights}", won?.nights == 1, Modifier.weight(1f))
            Stat(units.u, n0(units.cv(card.km)), won?.km == 1, Modifier.weight(1.2f))
            Stat("Flights", "${card.flights}", won?.flights == 1, Modifier.weight(1f))
            Stat("Farthest", n0(units.cv(card.farthestKm)), won?.farthest == 1, Modifier.weight(1.2f))
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "OPEN TRIP →", color = Poster.Red, style = PosterType.Caps.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.em),
                modifier = Modifier.clickable(role = Role.Button, onClick = onOpen).semantics { contentDescription = "Open trip ${card.title}" }.padding(vertical = 8.dp),
            )
            Box(Modifier.border(2.dp, Poster.Ink).door(Door.Trip(card.tripId, card.departed, card.returned, label)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                CapsText("Map", color = Poster.Ink, size = 11.sp)
            }
        }
    }
}

/**
 * The trips as a hand of cards, swiped along the top of the Dates tab, newest first. Each card is compared
 * with the older card beside it, a mustard tick on every stat it wins. Tapping a card opens its pass below.
 */
@Composable
internal fun TripDeck(cards: List<Card>, data: Record, units: Units, land: FeatureCollection?, onOpen: (Card) -> Unit, modifier: Modifier = Modifier, page: Int = 0, thumbs: Map<String, TrailLayout> = emptyMap()) {
    val n = cards.size
    val pager = rememberPagerState(initialPage = page, pageCount = { n })
    val scope = rememberCoroutineScope()
    LaunchedEffect(n) { if (n > 0 && pager.currentPage >= n) pager.scrollToPage(n - 1) }
    Column(modifier.fillMaxWidth()) {
        if (n == 0) {
            Text("No trips match", color = Poster.Ghost, style = PosterType.Lede.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(vertical = 40.dp))
            return@Column
        }
        HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 16.dp, beyondViewportPageCount = 1, verticalAlignment = Alignment.Top) { i ->
            TripCard(cards[i], cards.getOrNull(i + 1), data, units, land, selected = i == pager.currentPage, onOpen = { onOpen(cards[i]) }, thumb = thumbs[cards[i].tripId])
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val cur = pager.currentPage.coerceIn(0, n - 1)
            InkButton("←", { scope.launch { pager.animateScrollToPage(cur - 1) } }, Modifier.semantics { contentDescription = "Previous trip" }, quiet = true, enabled = cur > 0)
            CapsText("${cur + 1} of $n", size = 11.sp)
            InkButton("→", { scope.launch { pager.animateScrollToPage(cur + 1) } }, Modifier.semantics { contentDescription = "Next trip" }, quiet = true, enabled = cur < n - 1)
        }
    }
}
