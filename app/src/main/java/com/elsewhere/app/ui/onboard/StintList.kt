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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One chosen home or job with the months it ran. */
data class Stint(val name: String, val sub: String?, val from: Long, val to: Long?)

private val MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)
fun fmtMonth(m: YearMonth): String = MONTH.format(m).uppercase(Locale.US)

private data class Picking(val index: Int, val until: Boolean)

/**
 * Port of StintList in Onboarding.tsx: each stint with a "since" and an "until" month chip and a
 * Remove. Blank "until" means still; "since" at the record's start means the whole record.
 */
@Composable
fun StintList(
    items: List<Stint>, word: String, range: LongRange, removable: Boolean, hint: String?,
    onFrom: (Int, YearMonth) -> Unit, onTo: (Int, YearMonth?) -> Unit, onRemove: (Int) -> Unit,
) {
    var picking by remember { mutableStateOf<Picking?>(null) }
    val minM = monthOf(range.first); val maxM = monthOf(range.last)
    Column(Modifier.fillMaxWidth()) {
        if (hint != null) { Text(hint, color = Poster.Ink3, style = PosterType.Lede.copy(fontSize = 15.sp)); Spacer(Modifier.height(12.dp)) }
        items.forEachIndexed { i, h ->
            Box(Modifier.fillMaxWidth().height(2.dp).background(Poster.Ink))
            Column(Modifier.padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DisplayText(h.name, 24.sp, Modifier.weight(1f), maxLines = 1)
                    if (removable) CapsText("Remove", Modifier.clickable { onRemove(i) }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), color = Poster.Ink2, size = 11.sp)
                }
                if (h.sub != null) Text(h.sub, color = Poster.Ink3, style = PosterType.Label.copy(fontSize = 13.sp, letterSpacing = 0.sp))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    MonthChip("$word since", if (h.from <= range.first) null else monthOf(h.from)) { picking = Picking(i, false) }
                    MonthChip("until", h.to?.let(::monthOf)) { picking = Picking(i, true) }
                }
            }
        }
        if (items.isNotEmpty()) Box(Modifier.fillMaxWidth().height(2.dp).background(Poster.Ink))
    }
    picking?.let { p ->
        val h = items.getOrNull(p.index) ?: return@let
        if (p.until) MonthPickSheet("${h.name} · until", h.to?.let(::monthOf), maxOf(minM, monthOf(h.from)), maxM, clearLabel = "Still there", onPick = { onTo(p.index, it) }, onDismiss = { picking = null })
        else MonthPickSheet("${h.name} · $word since", monthOf(h.from), minM, h.to?.let { minOf(maxM, monthOf(it)) } ?: maxM, clearLabel = "Whole record", onPick = { onFrom(p.index, it ?: minM) }, onDismiss = { picking = null })
    }
}

@Composable
private fun MonthChip(label: String, value: YearMonth?, onClick: () -> Unit) {
    Row(Modifier.height(34.dp).border(2.dp, Poster.Ink).clickable(onClick = onClick).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        CapsText(label, size = 10.sp)
        Text(value?.let(::fmtMonth) ?: "—", color = Poster.Ink, style = PosterType.Label.copy(fontSize = 13.sp))
    }
}
