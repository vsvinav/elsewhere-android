package com.elsewhere.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val DOW = listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * Port of DatePick.tsx as a bottom sheet: one month at a time, days outside [min, max] dead,
 * the span to `other` shaded, the picked day on a red square. Picking a day closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickSheet(label: String, value: LocalDate, min: LocalDate, max: LocalDate, other: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Poster.Stock, contentColor = Poster.Ink, shape = RectangleShape, dragHandle = null) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp)) {
            CapsText("$label date")
            Spacer(Modifier.height(12.dp))
            Calendar(value, min, max, other, onPick = { onPick(it); onDismiss() })
        }
    }
}

/** The calendar itself, stateful over the month in view; snapshot-tested without the sheet. */
@Composable
fun Calendar(value: LocalDate, min: LocalDate, max: LocalDate, other: LocalDate, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier, initialMonth: YearMonth = YearMonth.from(value)) {
    var month by remember(value) { mutableStateOf(initialMonth) }
    val canPrev = month > YearMonth.from(min)
    val canNext = month < YearMonth.from(max)
    val lo = minOf(value, other); val hi = maxOf(value, other)
    val lead = (month.atDay(1).dayOfWeek.value + 6) % 7

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Arrow("‹", "Previous month", canPrev) { month = month.minusMonths(1) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                DisplayText(month.month.getDisplayName(TextStyle.FULL, Locale.US), 28.sp)
                Spacer(Modifier.size(8.dp))
                CapsText(month.year.toString(), Modifier.padding(bottom = 3.dp), color = Poster.Red, size = 12.sp)
            }
            Arrow("›", "Next month", canNext) { month = month.plusMonths(1) }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Poster.Ink))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            for (d in DOW) Text(d, Modifier.weight(1f), color = Poster.Ink3, textAlign = TextAlign.Center, style = PosterType.Caps.copy(fontSize = 11.sp, letterSpacing = 0.14.em))
        }
        Spacer(Modifier.height(6.dp))
        for (row in 0 until 6) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (col in 0 until 7) {
                    val n = row * 7 + col - lead + 1
                    if (n < 1 || n > month.lengthOfMonth()) { Spacer(Modifier.weight(1f).aspectRatio(1f)); continue }
                    val day = month.atDay(n)
                    Day(day, dead = day < min || day > max, pick = day == value, span = day > lo && day < hi, end = day == other, Modifier.weight(1f)) { onPick(day) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Perforation(color = Poster.Ink3)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("FIRST DAY", Modifier.clickable { onPick(min) }.padding(vertical = 6.dp), color = Poster.Ink2, style = PosterType.Caps.copy(fontSize = 11.sp, letterSpacing = 0.14.em))
            Text("LAST DAY", Modifier.clickable { onPick(max) }.padding(vertical = 6.dp), color = Poster.Ink2, style = PosterType.Caps.copy(fontSize = 11.sp, letterSpacing = 0.14.em))
        }
    }
}

@Composable
private fun Arrow(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).alpha(if (enabled) 1f else 0.3f).border(2.dp, Poster.Ink)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = Poster.Ink, style = PosterType.Display.copy(fontSize = 22.sp, lineHeight = 1.em)) }
}

@Composable
private fun Day(day: LocalDate, dead: Boolean, pick: Boolean, span: Boolean, end: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = when { pick -> Poster.Red; span -> Poster.Stock2; else -> Poster.Stock }
    Box(
        modifier.aspectRatio(1f).background(bg)
            .then(if (end && !pick) Modifier.border(2.dp, Poster.Ink) else Modifier)
            .semantics { selected = pick }
            .clickable(enabled = !dead, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.dayOfMonth.toString(),
            color = when { pick -> Poster.Stock; dead -> Poster.Ghost; else -> Poster.Ink },
            style = PosterType.Label.copy(fontSize = 13.sp, letterSpacing = 0.em, textDecoration = if (dead) TextDecoration.LineThrough else null),
        )
    }
}
