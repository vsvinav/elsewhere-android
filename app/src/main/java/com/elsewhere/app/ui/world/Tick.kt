package com.elsewhere.app.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A printed tick box with its sentence; the whole row toggles. */
@Composable
fun TickRow(checked: Boolean, onChange: (Boolean) -> Unit, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).border(2.dp, Poster.Ink).padding(3.dp).background(if (checked) Poster.Ink else Poster.Stock), contentAlignment = Alignment.Center) {
            if (checked) Text("✓", color = Poster.Stock, style = PosterType.Caps.copy(fontSize = 12.sp, letterSpacing = 0.sp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = Poster.Ink, style = PosterType.Lede.copy(fontSize = 15.sp))
    }
}
