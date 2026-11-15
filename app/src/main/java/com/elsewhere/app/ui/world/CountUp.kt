package com.elsewhere.app.ui.world

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/** Number that counts up from zero once on screen; `digits` fixed decimals. Settled at once without motion. */
@Composable
fun countUp(value: Double, digits: Int = 0): String {
    val motion = LocalMotion.current
    val shown = remember { Animatable(if (motion) 0f else value.toFloat()) }
    LaunchedEffect(value, motion) {
        if (motion) shown.animateTo(value.toFloat(), tween(1400, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)))
        else shown.snapTo(value.toFloat())
    }
    return if (digits == 0) n0(shown.value.toDouble()) else String.format(java.util.Locale.US, "%,.${digits}f", shown.value)
}
