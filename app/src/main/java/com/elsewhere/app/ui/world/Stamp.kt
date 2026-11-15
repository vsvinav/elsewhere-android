package com.elsewhere.app.ui.world

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** A red rubber stamp, slammed onto the sheet on first composition. */
@Composable
fun Stamp(text: String, modifier: Modifier = Modifier, color: Color = Poster.Red, animate: Boolean = true) {
    val scale = remember { Animatable(if (animate) 1.6f else 1f) }
    val alpha = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        launch { scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f)) }
        launch { alpha.animateTo(1f, tween(120)) }
    }
    Box(
        modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value; rotationZ = -12f }
            .border(4.dp, color, RoundedCornerShape(10.dp))
            .padding(3.dp)
            .border(1.5.dp, color, RoundedCornerShape(7.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text.uppercase(), color = color, textAlign = TextAlign.Center,
            style = PosterType.Caps.copy(fontSize = 12.sp, letterSpacing = 0.18.em, lineHeight = 1.15.em),
        )
    }
}
