package com.elsewhere.app.ui.onboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.Poster

enum class Step { Import, Key, Name, Home, Moves, Work, Units, Done }

/** Placeholder front door; the onboarding flow lands next. */
@Composable
fun OnboardingGraph(startStep: Step, onClose: (() -> Unit)?) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Poster.Stock).safeDrawingPadding().padding(20.dp)) {
        Pass { DisplayText("Front door", 48.sp, misreg = true) }
    }
}
