package com.elsewhere.app.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The three sheets a bill prints on, as in the web's `.bill`, `.bill.ink`, `.bill.red`. */
enum class Tone(val fill: Color, val text: Color, val muted: Color, val rule: Color) {
    STOCK(Poster.Stock, Poster.Ink, Poster.Ink3, Poster.Ink),
    INK(Poster.Ink, Poster.Stock, Poster.Stock3, Poster.Red),
    RED(Poster.Red, Poster.Stock, Poster.Stock.copy(alpha = 0.8f), Poster.Ink),
}

/** One full-bleed poster chapter: a rule on top, then the printed matter. With `share`, a Save menu sits in the bottom corner. */
@Composable
fun Bill(tone: Tone = Tone.STOCK, modifier: Modifier = Modifier, first: Boolean = false, share: Share? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth().background(tone.fill)) {
        Column {
            if (!first) Box(Modifier.fillMaxWidth().height(Rule).background(tone.rule))
            CompositionLocalProvider(LocalContentColor provides tone.text, LocalMuted provides tone.muted) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 48.dp), content = content)
            }
        }
        if (share != null) SaveMenu(share, card = { Bill(tone, first = first, content = content) }, Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 2.dp), color = tone.muted)
    }
}
