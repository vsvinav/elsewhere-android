package com.elsewhere.app.ui.onboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.Rule
import com.elsewhere.app.ui.world.Sticker

/** The web's `.ob-tag`: a pass for one ranked place; pressed, it prints in ink. */
@Composable
fun CandidateCard(c: Candidate, tag: String?, sub: String, selected: Boolean, onClick: () -> Unit, sticker: Boolean = false, modifier: Modifier = Modifier) {
    val bg = if (selected) Poster.Ink else Poster.Stock
    val fg = if (selected) Poster.Stock else Poster.Ink
    val dim = if (selected) Poster.Mustard else Poster.Ink3
    Column(modifier.fillMaxWidth().background(bg).border(2.dp, Poster.Ink).semantics { this.selected = selected }.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(Rule).background(if (selected) Poster.Red else Poster.Ink))
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (tag != null && !sticker) CapsText(tag, color = dim, size = 10.sp)
                    if (tag != null) Spacer(Modifier.height(8.dp))
                    DisplayText(c.label, 26.sp, color = fg, maxLines = 2)
                }
                if (tag != null && sticker) Sticker(tag, color = if (selected) Poster.Mustard else Poster.Red, textColor = if (selected) Poster.Ink else Poster.Stock)
            }
            Spacer(Modifier.height(8.dp))
            Text(sub, color = dim, style = PosterType.Label.copy(fontSize = 13.sp, letterSpacing = 0.sp))
            if (c.address != null) Text(c.address, color = dim, style = PosterType.Label.copy(fontSize = 12.sp, letterSpacing = 0.sp))
        }
    }
}

fun nights(n: Double): String = "${n.toInt()} ${if (n.toInt() == 1) "night" else "nights"}"
fun pct(share: Double): String = "${Math.round(share * 100)}% of nights then"
