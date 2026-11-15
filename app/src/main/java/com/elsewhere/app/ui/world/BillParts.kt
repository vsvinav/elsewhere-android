package com.elsewhere.app.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// The recurring printed matter of a bill (web .bill h2.word / .lede / .num / .foot / .legend).

/** The one-word chapter title, set huge. */
@Composable
fun Word(text: String, modifier: Modifier = Modifier, size: TextUnit = 64.sp, color: Color = LocalContentColor.current) {
    Text(text.uppercase(), modifier, color = color, style = PosterType.Display.copy(fontSize = size, letterSpacing = (-0.01).em))
}

@Composable
fun Lede(text: String, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Text(text, modifier, color = color, style = PosterType.Lede.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 1.35.em))
}

/** A big number with its unit set small beside it: "22 away". */
@Composable
fun BigNum(value: String, small: String? = null, modifier: Modifier = Modifier, size: TextUnit = 84.sp, color: Color = LocalContentColor.current, align: TextAlign = TextAlign.Start) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = if (align == TextAlign.End) Arrangement.End else Arrangement.Start) {
        Text(value, color = color, style = PosterType.Display.copy(fontSize = size, letterSpacing = (-0.01).em), softWrap = false)
        if (small != null) {
            Spacer(Modifier.width(6.dp))
            Text(small, Modifier.padding(bottom = 4.dp), color = color, style = PosterType.Display.copy(fontSize = size * 0.26f, letterSpacing = 0.02.em, fontWeight = FontWeight.ExtraBold))
        }
    }
}

/** The rule and the two bold lines that close a bill. */
@Composable
fun Foot(left: String, right: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(Rule).background(LocalContentColor.current))
        Spacer(Modifier.height(16.dp))
        val style = PosterType.Display.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.02.em, lineHeight = 1.em)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(left.uppercase(), Modifier.weight(1f, fill = false), style = style)
            if (right != null) { Spacer(Modifier.width(24.dp)); Text(right.uppercase(), style = style, textAlign = TextAlign.End) }
        }
    }
}

/** Colour key under a chart: a swatch then the name, in muted caps. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((name, c) in items) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(c).then(if (c == Poster.Stock2) Modifier.border(1.dp, Poster.Ghost) else Modifier))
            Spacer(Modifier.width(8.dp))
            Text(name.uppercase(), color = LocalMuted.current, style = PosterType.Caps.copy(fontSize = 11.sp, letterSpacing = 0.16.em))
        }
    }
}

/** One line of a .dates list: the place, a small note, the number and its unit. */
/** `id` names the place the row prints, when it should be looked up as it scrolls in. */
data class DateRow(val where: String, val whereSmall: String? = null, val n: String, val nSmall: String? = null, val id: String? = null)

private val Small = PosterType.Caps.copy(fontSize = 11.sp, letterSpacing = 0.2.em)

/** The web's `.dates` list: ruled rows set in bold display caps. `loud` sets the numbers red and bigger. */
@Composable
fun DatesList(rows: List<DateRow>, modifier: Modifier = Modifier, loud: Boolean = false, more: (@Composable () -> Unit)? = null, onSeen: (String) -> Unit = {}) {
    val ink = LocalContentColor.current
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(Rule).background(ink))
        for (r in rows) {
            Row(Modifier.fillMaxWidth().seenAs(r.id, onSeen).padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.where.uppercase(), Modifier.weight(1f, fill = false), style = PosterType.Display.copy(fontSize = if (loud) 34.sp else 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.01.em, lineHeight = 1.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (r.whereSmall != null) { Spacer(Modifier.width(10.dp)); Text(r.whereSmall.uppercase(), color = LocalMuted.current, style = Small, maxLines = 1) }
                }
                Spacer(Modifier.width(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.n, color = if (loud) Poster.Red else ink, style = PosterType.Display.copy(fontSize = if (loud) 34.sp else 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.em))
                    if (r.nSmall != null) { Spacer(Modifier.width(6.dp)); Text(r.nSmall.uppercase(), color = LocalMuted.current, style = Small) }
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(ink))
        }
        if (more != null) Box(Modifier.padding(top = 14.dp)) { more() }
    }
}

/** One bar of the setlist: a mode, its share of the longest bar, the figure. */
@Composable
fun SetlistRow(label: String, frac: Float, color: Color, n: String, small: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), Modifier.width(96.dp), style = PosterType.Display.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(22.dp).background(Poster.Stock2)) { Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(22.dp).background(color)) }
        Spacer(Modifier.width(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(n, style = PosterType.Display.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.em))
            Spacer(Modifier.width(4.dp))
            Text(small.uppercase(), color = LocalMuted.current, style = Small)
        }
    }
}

/** The web's `.ptable`: caps headers over a rule, then rows of display figures, first column larger. */
@Composable
fun PTable(headers: List<String>, rows: List<List<String>>, modifier: Modifier = Modifier, compact: Boolean = false) {
    val ink = LocalContentColor.current
    val first = PosterType.Display.copy(fontSize = if (compact) 24.sp else 36.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.em)
    val cell = PosterType.Display.copy(fontSize = if (compact) 22.sp else 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.em)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            headers.forEachIndexed { i, h ->
                Text(h.uppercase(), Modifier.weight(if (i == 0) 1.4f else 1f), color = LocalMuted.current, style = PosterType.Caps.copy(fontSize = 10.sp, letterSpacing = 0.12.em), maxLines = 1, textAlign = if (i == 0) TextAlign.Start else TextAlign.End)
            }
        }
        Box(Modifier.fillMaxWidth().height(Rule).background(ink))
        for (r in rows) {
            Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 10.dp else 12.dp), verticalAlignment = Alignment.Bottom) {
                r.forEachIndexed { i, v ->
                    Text(v.uppercase(), Modifier.weight(if (i == 0) 1.4f else 1f), style = if (i == 0) first else cell, textAlign = if (i == 0) TextAlign.Start else TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(ink))
        }
    }
}

/** The web's `.plist`: rank in red, name over a note, figure at right. */
@Composable
fun PList(rows: List<PRow>, modifier: Modifier = Modifier, onSeen: (String) -> Unit = {}) {
    val ink = LocalContentColor.current
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(Rule).background(ink))
        rows.forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().seenAs(r.id, onSeen).padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
                Text("${i + 1}", Modifier.width(36.dp).padding(top = 6.dp), color = Poster.Red, style = PosterType.Display.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold))
                Column(Modifier.weight(1f)) {
                    Text(r.name.uppercase(), style = PosterType.Display.copy(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (r.sub != null) Text(r.sub, Modifier.padding(top = 4.dp), color = LocalMuted.current, style = PosterType.Label.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 1.3.em))
                }
                Spacer(Modifier.width(16.dp))
                Text(r.n, style = PosterType.Display.copy(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.em))
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(ink))
        }
    }
}

data class PRow(val name: String, val sub: String?, val n: String, val id: String? = null)

/** A row naming a place asks for its lookup once it is on screen; rows without an id are left alone. */
private fun Modifier.seenAs(id: String?, onSeen: (String) -> Unit) = if (id == null) this else onFirstVisible { onSeen(id) }
