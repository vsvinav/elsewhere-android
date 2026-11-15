package com.elsewhere.app.ui.onboard

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ui.shell.Perforation
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Stands in for the web's `<input type="month">`: one year at a time, twelve chips, clamped to the record. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPickSheet(label: String, value: YearMonth?, min: YearMonth, max: YearMonth, clearLabel: String?, onPick: (YearMonth?) -> Unit, onDismiss: () -> Unit) {
    var year by remember { mutableIntStateOf((value ?: max).year.coerceIn(min.year, max.year)) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Poster.Stock, contentColor = Poster.Ink, shape = RectangleShape, dragHandle = null) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp)) {
            CapsText(label)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Arrow("‹", year > min.year) { year-- }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { DisplayText(year.toString(), 28.sp) }
                Arrow("›", year < max.year) { year++ }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(Poster.Ink))
            Spacer(Modifier.height(8.dp))
            for (row in 0 until 4) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0 until 3) {
                        val m = YearMonth.of(year, Month.of(row * 3 + col + 1))
                        val dead = m < min || m > max
                        val on = m == value
                        Box(
                            Modifier.weight(1f).height(44.dp).alpha(if (dead) 0.3f else 1f).background(if (on) Poster.Red else Poster.Stock).border(2.dp, if (on) Poster.Red else Poster.Ink)
                                .clickable(enabled = !dead) { onPick(m); onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(m.month.getDisplayName(TextStyle.SHORT, Locale.US).uppercase(), color = if (on) Poster.Stock else Poster.Ink, style = PosterType.Caps.copy(fontSize = 12.sp, letterSpacing = 0.14.em))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (clearLabel != null) {
                Spacer(Modifier.height(6.dp))
                Perforation(color = Poster.Ink3)
                Text(clearLabel.uppercase(), Modifier.clickable { onPick(null); onDismiss() }.padding(vertical = 12.dp), color = Poster.Ink2, style = PosterType.Caps.copy(fontSize = 11.sp, letterSpacing = 0.14.em))
            }
        }
    }
}

@Composable
private fun Arrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).alpha(if (enabled) 1f else 0.3f).border(2.dp, Poster.Ink).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = Poster.Ink, style = PosterType.Display.copy(fontSize = 22.sp, lineHeight = 1.em)) }
}
