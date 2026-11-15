package com.elsewhere.app.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.data.Place

/** A small pill slapped on at an angle. */
@Composable
fun Sticker(text: String, modifier: Modifier = Modifier, color: Color = Poster.Red, textColor: Color = Poster.Stock) {
    CapsText(
        text, modifier.rotate(-6f).background(color, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
        color = textColor, size = 11.sp,
    )
}

/** A place slapped on the sheet: photo (or blank stock), name, where; optional stamp in the corner. */
@Composable
fun PlaceSticker(
    place: Place?, name: String, sub: String?, modifier: Modifier = Modifier, tilt: Float = -1.5f,
    onClick: (() -> Unit)? = null, stamp: (@Composable () -> Unit)? = null,
) {
    Box(modifier.rotate(tilt)) {
        Column(
            Modifier
                .background(Color.White).border(2.dp, Poster.Ink)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 16.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides Poster.Ink, LocalMuted provides Poster.Ink3) {
                PlacePhoto(place, blank = true)
                Spacer(Modifier.height(12.dp))
                Text(name.uppercase(), color = Poster.Ink, style = PosterType.Display.copy(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 0.95.em), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!sub.isNullOrEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(sub, color = Poster.Ink3, style = PosterType.Label.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 1.3.em))
                }
            }
        }
        if (stamp != null) Box(Modifier.align(Alignment.BottomEnd).offset(16.dp, 16.dp)) { stamp() }
    }
}
