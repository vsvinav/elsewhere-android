package com.elsewhere.app.map

import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * "Frame these bounds" needs the view's size in px, which Compose reports only after layout. A fit asked before
 * then waits for the first size and runs once; a later size change (rotation) leaves the camera where it is.
 * Every fit glides rather than jumps, so the user sees where the pins went. Zoom counts 256 logical px per
 * tile and Compose reports physical px, so size and padding are scaled back to dp before solving.
 */
class Framer(private val go: (bounds: Bounds, sizePx: IntSize, paddingPx: Int) -> Unit) {
    /** The Mercator solve, for a host whose camera is set by hand. */
    constructor(move: (cam: Cam, animateMs: Int) -> Unit) : this({ b, size, pad -> move(camForBounds(b, size.width, size.height, pad), ANIMATE_MS) }) {
        solveInDp = true
    }

    private var solveInDp = false
    var density = 1f
    var size: IntSize = IntSize.Zero
        set(v) {
            field = v
            if (v != IntSize.Zero) pending?.let { pending = null; it() }
        }
    private var pending: (() -> Unit)? = null

    fun fit(bounds: Bounds, paddingPx: Int) {
        val run = {
            if (solveInDp) go(bounds, IntSize((size.width / density).roundToInt(), (size.height / density).roundToInt()), (paddingPx / density).roundToInt())
            else go(bounds, size, paddingPx)
        }
        if (size != IntSize.Zero) run() else pending = run
    }

    companion object { const val ANIMATE_MS = 400 }
}
