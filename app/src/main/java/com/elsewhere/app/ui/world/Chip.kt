package com.elsewhere.app.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * A bordered poster chip: inked when `on`, stock otherwise. A checkbox or radio chip toggles or selects; a button
 * chip just fires. The label doubles as its content description.
 */
@Composable
fun Chip(label: String, n: String?, on: Boolean, role: Role = Role.Checkbox, swatch: Color? = null, onClick: () -> Unit) {
    val act = when (role) {
        Role.RadioButton -> Modifier.selectable(selected = on, role = role, onClick = onClick)
        Role.Button -> Modifier.clickable(role = role, onClick = onClick)
        else -> Modifier.toggleable(value = on, role = role, onValueChange = { onClick() })
    }
    Row(
        Modifier.background(if (on) Poster.Ink else Poster.Stock).border(2.dp, Poster.Ink).then(act)
            .semantics { contentDescription = label }
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = PosterType.Display.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.03.em, lineHeight = 1.em)
        if (swatch != null) { Box(Modifier.size(9.dp).background(swatch).border(1.dp, if (on) Poster.Stock else Poster.Ink)); Spacer(Modifier.width(6.dp)) }
        Text(label.uppercase(), color = if (on) Poster.Stock else Poster.Ink, style = style, softWrap = false)
        if (n != null) { Spacer(Modifier.width(6.dp)); Text(n, color = if (on) Poster.Mustard else Poster.Red, style = style) }
    }
}
