package com.elsewhere.app.ui.world

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ingest.Progress

/** The web's `.naming`: a small red stamp that turns while Places lookups land, then lifts off. */
@Composable
fun NamingStamp(progress: Progress?, modifier: Modifier = Modifier) {
    if (progress == null) return
    val motion = LocalMotion.current
    val turn by rememberInfiniteTransition(label = "stamp").animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "turn")
    Box(
        modifier.size(92.dp).rotate(-9f).background(Poster.Stock, CircleShape).border(4.dp, Poster.Red, CircleShape)
            .semantics { contentDescription = "Naming places, ${progress.done} of ${progress.total}"; liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(8.dp).rotate(if (motion) turn else 0f)) {
            drawCircle(Poster.Red, radius = size.minDimension / 2, center = Offset(size.width / 2, size.height / 2), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("NAMING\nPLACES", color = Poster.Red, textAlign = TextAlign.Center, style = PosterType.Caps.copy(fontSize = 8.sp, letterSpacing = 0.18.em, lineHeight = 1.1.em))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${progress.done}", color = Poster.Red, style = PosterType.Display.copy(fontSize = 24.sp, lineHeight = 0.9.em))
                Text("/${progress.total}", color = Poster.Red, style = PosterType.Label.copy(fontSize = 9.sp, letterSpacing = 0.08.em))
            }
        }
    }
}
