package com.elsewhere.app.ui.tabs.replay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.analytics.Rail
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import kotlin.math.roundToInt

// Port of web/src/map/Rail.tsx: the "so far" figures that print while a finger holds the strip.

/** Four figures on an ink block, re-set every frame of a scrub; the milestone prints in red under a hairline. */
@Composable
fun Rail(rail: Rail, visible: Boolean, noHome: Boolean = false, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(rail.milestone) { if (visible && rail.milestone != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    AnimatedVisibility(
        visible, modifier,
        enter = if (motion) fadeIn() + expandVertically() else EnterTransition.None,
        exit = if (motion) fadeOut() + shrinkVertically() else ExitTransition.None,
    ) {
        Column(Modifier.fillMaxWidth().background(Poster.Ink).padding(horizontal = 12.dp, vertical = 8.dp).testTag("rail")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Figure("KM SO FAR", "%,d".format(java.util.Locale.US, rail.km.roundToInt()))
                Figure("CITIES", rail.cities.toString())
                Figure("NIGHTS AWAY", if (noHome) "—" else rail.nightsAway.toString())
                Figure("FLIGHTS", rail.flights.toString())
            }
            if (rail.milestone != null) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Poster.Ink3))
                Spacer(Modifier.height(5.dp))
                Text(
                    rail.milestone, color = Poster.Red, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = PosterType.Display.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.em),
                )
            }
        }
    }
}

@Composable
private fun Figure(label: String, figure: String) {
    Column {
        Text(label, color = Poster.Stock3, style = PosterType.Caps.copy(fontSize = 9.sp), softWrap = false)
        Text(figure, color = Poster.Stock, style = PosterType.Display.copy(fontSize = 32.sp, lineHeight = 1.em), softWrap = false)
    }
}
