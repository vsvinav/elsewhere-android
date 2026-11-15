package com.elsewhere.app.ui.world

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.data.Ready
import kotlinx.coroutines.delay

/** How long the finished bar stays before it lifts off (`.ready[data-done]`). */
const val READY_LINGER_MS = 600L

/** The web's `.ready`: a 3dp red line on an ink track along the top edge that fills as the record is built, then lifts off. */
@Composable
fun ReadyBar(ready: Ready?, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    var gone by remember { mutableStateOf(false) }
    LaunchedEffect(ready) {
        gone = false
        if (ready != null && ready.pct >= 100) { delay(READY_LINGER_MS); gone = true }
    }
    if (ready == null || gone) return
    val done = ready.pct >= 100
    val fill by animateFloatAsState(ready.pct / 100f, if (motion) tween(400) else tween(0), label = "fill")
    val fade by animateFloatAsState(if (done) 0f else 1f, if (motion) tween(READY_LINGER_MS.toInt()) else tween(0), label = "fade")
    Box(
        modifier.fillMaxWidth().alpha(if (motion) fade else 1f)
            .semantics { contentDescription = ready.label; progressBarRangeInfo = ProgressBarRangeInfo(ready.pct.toFloat(), 0f..100f) },
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Poster.Ink)) {
            Box(Modifier.fillMaxWidth(fill.coerceIn(0f, 1f)).fillMaxHeight().background(Poster.Red))
        }
        // Archivo has no small-caps feature, so the label is set in capitals as CapsText does.
        Text(
            ready.label.uppercase(), Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 12.dp), color = Poster.Ink, maxLines = 1,
            style = PosterType.Caps.copy(fontSize = 10.sp, letterSpacing = 0.12.em),
        )
    }
}
