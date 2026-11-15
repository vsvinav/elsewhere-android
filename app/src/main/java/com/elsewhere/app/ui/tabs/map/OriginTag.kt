package com.elsewhere.app.ui.tabs.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ui.nav.Origin
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType

private const val FADE_MS = 160

/**
 * The stub left in the reader's hand when a figure opened the map: "FROM · 42 COUNTRIES ✕", mustard on ink,
 * torn along a perforation before the cross. Tapping anywhere on it goes back to the bill it came from.
 */
@Composable
fun OriginTag(origin: Origin?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    // The stub keeps its last print while it fades out.
    var shown by remember { mutableStateOf(origin) }
    if (origin != null) shown = origin
    val ms = if (LocalMotion.current) FADE_MS else 0
    AnimatedVisibility(origin != null, modifier, enter = fadeIn(tween(ms)), exit = fadeOut(tween(ms))) {
        val o = shown ?: return@AnimatedVisibility
        val style = PosterType.Display.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.03.em, lineHeight = 1.em)
        Row(
            Modifier.height(32.dp).background(Poster.Ink)
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = "Back to ${o.backTab.label}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("FROM · ${o.label.uppercase()}", Modifier.widthIn(max = 220.dp).padding(start = 10.dp, end = 8.dp), color = Poster.Mustard, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Spacer(Modifier.width(1.dp).fillMaxHeight().drawBehind {
                drawLine(Poster.Mustard, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
            })
            Text("✕", Modifier.padding(horizontal = 8.dp), color = Poster.Mustard, style = style)
        }
    }
}
