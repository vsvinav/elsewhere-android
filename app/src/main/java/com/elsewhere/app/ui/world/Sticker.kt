package com.elsewhere.app.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A small pill slapped on at an angle. */
@Composable
fun Sticker(text: String, modifier: Modifier = Modifier, color: Color = Poster.Red, textColor: Color = Poster.Stock) {
    CapsText(
        text, modifier.rotate(-6f).background(color, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
        color = textColor, size = 11.sp,
    )
}
