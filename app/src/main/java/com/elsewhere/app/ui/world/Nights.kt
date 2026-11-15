package com.elsewhere.app.ui.world

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elsewhere.app.analytics.Night
import com.elsewhere.app.analytics.NightKind
import com.elsewhere.app.ui.world.figures.Beds

// Port of web/src/world/Nights.tsx: every night in the range as one cell.

data class NightsUi(val nights: List<Night>, val away: Int, val fresh: Int, val cities: Int, val known: Int, val span: String, val title: String) {
    val lede: String get() = "$away of $known nights somewhere other than home" +
        (if (cities > 0) ", across $cities ${plural(cities, "city", "cities")}" else "") + ". " +
        if (fresh > 0) "$fresh of them in a city you'd never slept in before." else "None of them anywhere new."
}

/** Null when nothing in the range was recorded at all, so the bill prints nothing. */
fun nightsUi(nights: List<Night>, range: LongRange, name: String?): NightsUi? {
    val away = nights.filter { it.kind == NightKind.NEW || it.kind == NightKind.REPEAT }
    val known = nights.count { it.kind != NightKind.NONE }
    if (known == 0) return null
    val cities = away.mapNotNull { it.city?.takeIf { c -> c.isNotEmpty() } }.toSet().size
    val span = if (yearOf(range.first) == yearOf(range.last)) yearOf(range.first).toString() else "${monthLong(range.first)} – ${monthLong(range.last)}"
    val title = (if (!name.isNullOrEmpty()) "${name.uppercase()}'S " else "") + "ELSEWHERE ${span.uppercase()}"
    return NightsUi(nights, away.size, nights.count { it.kind == NightKind.NEW }, cities, known, span, title)
}

val NIGHT_LEGEND = listOf("Home" to Poster.Ink, "Somewhere new" to Poster.Red, "Been before" to Poster.Mustard, "No record" to Poster.Stock2)

@Composable
fun NightsBill(ui: NightsUi, modifier: Modifier = Modifier, first: Boolean = false) {
    Bill(Tone.STOCK, modifier, first = first) {
        Word("Nights")
        Spacer(Modifier.height(16.dp))
        Lede(ui.lede)
        Spacer(Modifier.height(24.dp))
        BigNum(countUp(ui.away.toDouble()), "away", Modifier.fillMaxWidth(), align = TextAlign.End)
        Spacer(Modifier.height(40.dp))
        Beds(ui.nights, Modifier.fillMaxWidth(), maxHeight = 170.dp)
        Spacer(Modifier.height(14.dp))
        Legend(NIGHT_LEGEND)
        Spacer(Modifier.height(40.dp))
        Foot(ui.span, "Every night, one cell")
    }
}
