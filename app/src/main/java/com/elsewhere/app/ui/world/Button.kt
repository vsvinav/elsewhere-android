package com.elsewhere.app.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The web's `.btn`: display caps in an ink block with a 2dp border; `quiet` prints the same outline on bare stock. `icon` sits left of the word. */
@Composable
fun InkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, quiet: Boolean = false, enabled: Boolean = true, red: Boolean = false, icon: (@Composable () -> Unit)? = null) {
    val fill = when { red -> Poster.Red; quiet -> Poster.Stock; else -> Poster.Ink }
    val edge = if (red) Poster.Red else Poster.Ink
    val color = if (quiet && !red) Poster.Ink else Poster.Stock
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.35f)
            .background(fill)
            .border(2.dp, edge)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.invoke()
            Text(text.uppercase(), color = color, style = PosterType.Display.copy(fontSize = 16.sp, letterSpacing = 0.14.em, lineHeight = 1.em), maxLines = 1)
        }
    }
}
