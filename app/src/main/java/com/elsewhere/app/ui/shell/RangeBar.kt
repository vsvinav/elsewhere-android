package com.elsewhere.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

/** "12 MAY 2024" the way DatePick.tsx prints a chip. */
fun shortDate(day: LocalDate): String = SHORT.format(day).uppercase(Locale.US)

/** Port of RangeBar.tsx: two date chips and the preset chips, one row above the ticket strip. */
@Composable
fun RangeBar(
    from: LocalDate, to: LocalDate, years: List<Int>, isPreset: (Preset) -> Boolean,
    onFrom: () -> Unit, onTo: () -> Unit, onPreset: (Preset) -> Unit, modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().background(Poster.Stock).padding(horizontal = 12.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        DateChip("From", from, onFrom)
        DateChip("To", to, onTo)
        PresetChip("All", isPreset(Preset.All)) { onPreset(Preset.All) }
        for (y in years) PresetChip(y.toString(), isPreset(Preset.Year(y))) { onPreset(Preset.Year(y)) }
        PresetChip("90d", isPreset(Preset.Last90)) { onPreset(Preset.Last90) }
    }
}

@Composable
fun DateChip(label: String, day: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(36.dp).border(2.dp, Poster.Ink).clickable(onClick = onClick).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), color = Poster.Ink3, style = PosterType.Caps.copy(fontSize = 10.sp, letterSpacing = 0.14.em))
        Text(shortDate(day), color = Poster.Ink, style = PosterType.Display.copy(fontSize = 17.sp, letterSpacing = 0.02.em, lineHeight = 1.em), maxLines = 1)
        Text("▾", color = Poster.Ink3, style = PosterType.Label.copy(fontSize = 10.sp))
    }
}

@Composable
private fun PresetChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).background(if (on) Poster.Ink else Poster.Stock).border(2.dp, Poster.Ink)
            .semantics { selected = on }.clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.uppercase(), color = if (on) Poster.Stock else Poster.Ink, style = PosterType.Caps.copy(fontSize = 11.sp, letterSpacing = 0.14.em), maxLines = 1)
    }
}
