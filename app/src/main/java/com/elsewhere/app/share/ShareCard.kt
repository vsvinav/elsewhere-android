package com.elsewhere.app.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType

/** True while a card is being printed for sharing, so bills and passes leave their controls off the sheet. */
val LocalPrinting = staticCompositionLocalOf { false }

/**
 * One bill or pass printed on its own stock at the sheet size of `format`. The card is laid out at
 * [CARD_DP] wide and its density picked so it fills its box, so a tall card on a story shrinks rather
 * than overflowing. Pure: the caller provides the theme and turns motion off.
 */
@Composable
fun ShareCard(format: ShareFormat, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPrinting provides true) {
        SubcomposeLayout(Modifier.background(Poster.Stock)) { _ ->
            fun card(density: Float) = subcompose(density) {
                CompositionLocalProvider(LocalDensity provides Density(density)) { content() }
            }.first().measure(Constraints.fixedWidth((CARD_DP * density).toInt()))

            // Height in dp does not depend on density (the width in dp is fixed), so one probe tells us the fit.
            val probeDensity = cardPlacement(format, 1f).density
            val probe = card(probeDensity)
            val placement = cardPlacement(format, probe.height / probeDensity)
            val card = if (placement.density == probeDensity) probe else card(placement.density)
            val foot = if (format.sheetHeight != null) subcompose("foot") { Footer() }.first().measure(Constraints.fixed(placement.sheetWidth, FOOT_PX)) else null
            layout(placement.sheetWidth, placement.sheetHeight) {
                card.place(placement.x, placement.y)
                foot?.place(0, placement.sheetHeight - FOOT_PX)
            }
        }
    }
}

/** The brand line under a story or post: a 3px ink rule, the name on the left, the claim on the right. Sized in sheet pixels. */
@Composable
private fun Footer() {
    CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 60.dp)) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(Poster.Ink))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ELSEWHERE", color = Poster.Ink, style = PosterType.Display.copy(fontSize = 34.sp))
                Text("MY WORLD TOUR · FROM MY GOOGLE TIMELINE", color = Poster.Ink, style = PosterType.Label.copy(fontSize = 13.sp))
            }
        }
    }
}
