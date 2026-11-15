package com.elsewhere.app.ui.world

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

// Port of web/src/world/useInView.ts: fire once, the first time an element comes near the viewport.

/** The web's `rootMargin: 200px`: rows just below the fold are asked for before they scroll in. */
val VISIBLE_MARGIN = 200.dp

/** Is `bounds` (window pixels) within `margin` of a window `width` × `height`? Pure, so the rule can be tested. */
fun nearViewport(bounds: Rect, width: Float, height: Float, margin: Float): Boolean =
    bounds.right >= -margin && bounds.bottom >= -margin && bounds.left <= width + margin && bounds.top <= height + margin

/** Calls `onVisible` once per composable instance, the first time it is laid out near the window. */
fun Modifier.onFirstVisible(onVisible: () -> Unit): Modifier = composed {
    val view = LocalView.current
    val margin = with(LocalDensity.current) { VISIBLE_MARGIN.toPx() }
    val cb by rememberUpdatedState(onVisible)
    var fired by remember { mutableStateOf(false) }
    if (fired) Modifier else Modifier.onGloballyPositioned { c ->
        val o = c.positionInWindow()
        val r = Rect(o.x, o.y, o.x + c.size.width, o.y + c.size.height)
        if (!fired && nearViewport(r, view.width.toFloat(), view.height.toFloat(), margin)) { fired = true; cb() }
    }
}
