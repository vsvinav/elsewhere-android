package com.elsewhere.app.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ui.Tab
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType

/** Read out for the dot that marks a stub with someone live behind it. */
const val LIVE_DOT = "Someone is live"

/** The web's `.ticket`: a perforated strip of stubs along the bottom, one per section. */
@Composable
fun TicketStrip(selected: Tab, onSelect: (Tab) -> Unit, onBrand: () -> Unit, modifier: Modifier = Modifier, dots: Set<Tab> = emptySet()) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var selectedX by remember { mutableFloatStateOf(0f) }
    var selectedW by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(selected, selectedX, selectedW) {
        val view = scroll.viewportSize
        if (view == 0) return@LaunchedEffect
        val pad = with(density) { 16.dp.toPx() }
        val lo = (selectedX - pad).toInt(); val hi = (selectedX + selectedW + pad - view).toInt()
        if (scroll.value > lo) scroll.animateScrollTo(lo.coerceAtLeast(0)) else if (scroll.value < hi) scroll.animateScrollTo(hi)
    }
    Column(modifier.fillMaxWidth().background(Poster.Stock)) {
        Perforation()
        Row(Modifier.fillMaxWidth().height(64.dp).horizontalScroll(scroll).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Stub(selected = false, onClick = onBrand) {
                Text(
                    buildAnnotatedString { append("ELSE"); withStyle(SpanStyle(color = Poster.Red)) { append("WHERE") } },
                    color = Poster.Ink, style = PosterType.Display.copy(fontSize = 20.sp, letterSpacing = 0.02.em, lineHeight = 1.em), maxLines = 1,
                )
            }
            for (t in Tab.entries) {
                val on = t == selected
                Stub(
                    selected = on, onClick = { onSelect(t) },
                    modifier = Modifier.onGloballyPositioned { if (on) { selectedX = it.positionInParent().x; selectedW = it.size.width.toFloat() } },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.label.uppercase(), color = if (on) Poster.Stock else Poster.Ink, style = PosterType.Caps.copy(fontSize = 12.sp, letterSpacing = 0.04.em), maxLines = 1)
                        if (t in dots) Box(Modifier.padding(start = 5.dp).size(6.dp).clip(CircleShape).background(if (on) Poster.Stock else Poster.Red).semantics { contentDescription = LIVE_DOT })
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun Stub(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .padding(end = 6.dp)
            .fillMaxHeight()
            .background(if (selected) Poster.Red else Poster.Stock)
            .border(2.dp, if (selected) Poster.Red else Poster.Ink)
            .semantics { this.selected = selected }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The tear line: a dashed 1dp rule across the sheet. */
@Composable
fun Perforation(modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = Poster.Ink) {
    val density = LocalDensity.current
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        val dash = with(density) { 6.dp.toPx() }
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
    }
}
