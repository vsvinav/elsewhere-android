package com.elsewhere.app.map

import androidx.compose.ui.unit.IntSize

/**
 * "Frame these bounds" needs the view's size in px, which Compose reports only after layout. A fit asked before
 * then waits for the first size and runs once; a later size change (rotation) leaves the camera where it is.
 * Every fit glides rather than jumps, so the user sees where the pins went.
 */
class Framer(private val move: (cam: Cam, animateMs: Int) -> Unit) {
    var size: IntSize = IntSize.Zero
        set(v) {
            field = v
            if (v != IntSize.Zero) pending?.let { pending = null; it() }
        }
    private var pending: (() -> Unit)? = null

    fun fit(bounds: Bounds, paddingPx: Int) {
        val go = { move(camForBounds(bounds, size.width, size.height, paddingPx), ANIMATE_MS) }
        if (size != IntSize.Zero) go() else pending = go
    }

    companion object { const val ANIMATE_MS = 400 }
}
