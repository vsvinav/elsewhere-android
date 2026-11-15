package com.elsewhere.app.ui.world

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import com.elsewhere.app.ui.nav.Door
import com.elsewhere.app.ui.nav.DoorText
import com.elsewhere.app.ui.nav.Span
import com.elsewhere.app.ui.nav.door

/**
 * A printed input: ink on stock, a rule beneath instead of a box. `display` sets the headline
 * face the web uses for the name question; `mono` the typewriter face for keys.
 */
@Composable
fun PosterTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "",
    label: String? = null, display: Boolean = false, mono: Boolean = false, singleLine: Boolean = true,
) {
    val style: TextStyle = when {
        display -> PosterType.Display.copy(fontSize = 56.sp, lineHeight = 1.em, color = Poster.Ink)
        mono -> PosterType.Lede.copy(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = Poster.Ink)
        else -> PosterType.Lede.copy(color = Poster.Ink)
    }
    val rule: Dp = if (display) 4.dp else 2.dp
    Column(modifier) {
        if (label != null) { CapsText(label, size = 10.sp); Spacer(Modifier.height(8.dp)) }
        BasicTextField(
            value = if (display) value.uppercase() else value, onValueChange = onValueChange, textStyle = style, singleLine = singleLine,
            cursorBrush = SolidColor(Poster.Red),
            keyboardOptions = KeyboardOptions(capitalization = if (display) KeyboardCapitalization.Words else KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(Modifier.padding(vertical = if (display) 6.dp else 10.dp)) {
                    if (value.isEmpty()) Text(if (display) placeholder.uppercase() else placeholder, color = Poster.Ghost, style = style)
                    inner()
                }
            },
        )
        Box(Modifier.fillMaxWidth().height(rule).background(Poster.Ink))
    }
}

/**
 * The web's `Field`: a caps label over a value. `big` sets the value in display numerals; an empty
 * value prints as a ghost "not on record" (or "—" when big), as the web does.
 */
@Composable
fun Field(label: String, value: String?, modifier: Modifier = Modifier, big: Boolean = false, door: Door? = null) {
    val onInk = LocalContentColor.current == Poster.Stock
    val empty = value.isNullOrEmpty()
    Column(modifier.door(door)) {
        CapsText(label, size = 11.sp)
        Spacer(Modifier.height(6.dp))
        val color = if (empty) (if (onInk) Poster.Ink3 else Poster.Ghost) else LocalContentColor.current
        val shown = if (empty) (if (big) "—" else "not on record") else value!!
        if (big) Text(shown.uppercase(), color = color, style = PosterType.Display.copy(fontSize = 44.sp, lineHeight = 0.9.em), maxLines = 1)
        else Text(shown, color = color, style = PosterType.Lede.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.2.em))
    }
}

/** A field whose value holds doors of its own. */
@Composable
fun Field(label: String, value: List<Span>, modifier: Modifier = Modifier) {
    Column(modifier) {
        CapsText(label, size = 11.sp)
        Spacer(Modifier.height(6.dp))
        DoorText(value, color = LocalContentColor.current, style = PosterType.Lede.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.2.em))
    }
}
