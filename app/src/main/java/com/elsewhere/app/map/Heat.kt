package com.elsewhere.app.map

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import kotlin.math.exp
import kotlin.math.roundToInt

// Port of web/src/map/heat.ts: the Map tab's heat toggle, warmest where we go most often. A true cold→hot palette,
// never the poster red, which elsewhere marks the new and the rarely visited.

/** Cold→hot stops as RGBA 0..255, matching the web's HEAT_STOPS. */
val HEAT_STOPS: List<IntArray> = listOf(
    intArrayOf(43, 75, 209, 0),     // 0.00 transparent blue
    intArrayOf(43, 75, 209, 160),   // 0.20 blue
    intArrayOf(47, 191, 113, 200),  // 0.40 green
    intArrayOf(242, 225, 76, 230),  // 0.60 yellow
    intArrayOf(240, 128, 32, 245),  // 0.80 orange
    intArrayOf(214, 60, 32, 255),   // 1.00 red
)
const val HEAT_RADIUS_PX = 40f      // kernel radius in screen px (scale by density)
const val HEAT_THRESHOLD = 0.05f

/** ARGB int, piecewise-linear between stops, `t` clamped 0..1. */
fun heatColor(t: Float): Int {
    val u = (if (t.isNaN()) 0f else t.coerceIn(0f, 1f)) * (HEAT_STOPS.size - 1)
    val i = u.toInt().coerceAtMost(HEAT_STOPS.size - 2)
    val f = u - i
    val a = HEAT_STOPS[i]; val b = HEAT_STOPS[i + 1]
    fun ch(c: Int) = (a[c] + (b[c] - a[c]) * f).roundToInt().coerceIn(0, 255)
    return (ch(3) shl 24) or (ch(0) shl 16) or (ch(1) shl 8) or ch(2)
}

/** How warm a stop makes its patch: how often we went. */
fun heatWeight(d: Dot): Double = d.place.nVisits.toDouble()

/**
 * Splats weighted dots into a coarse grid of `cell` px, a Gaussian-ish kernel of `radiusPx` around each; returns
 * normalised 0..1 floats row-major. O(points × kernel cells): each point touches at most (2r/cell + 1)² cells.
 */
fun heatGrid(points: List<Offset>, weights: DoubleArray, w: Int, h: Int, cell: Int, radiusPx: Float): FloatArray {
    val cols = maxOf(1, w / cell); val rows = maxOf(1, h / cell)
    val grid = FloatArray(cols * rows)
    if (points.isEmpty()) return grid
    val r = radiusPx / cell
    val reach = kotlin.math.ceil(r).toInt()
    val r2 = r * r
    // Falls to ~e⁻² at the radius; one row of the kernel is reused across every point.
    val sigma2 = r2 / 4f
    val falloff = FloatArray(reach * reach * 2 + 1).also { for (d2 in it.indices) it[d2] = if (d2 <= r2) exp(-d2 / sigma2) else 0f }
    var max = 0f
    for (n in points.indices) {
        val p = points[n]
        val cx = p.x / cell; val cy = p.y / cell
        val ix = cx.toInt(); val iy = cy.toInt()
        if (ix < -reach || iy < -reach || ix >= cols + reach || iy >= rows + reach) continue
        val wt = weights[n].toFloat()
        for (gy in maxOf(0, iy - reach)..minOf(rows - 1, iy + reach)) {
            val dy = gy - iy
            val row = gy * cols
            for (gx in maxOf(0, ix - reach)..minOf(cols - 1, ix + reach)) {
                val dx = gx - ix
                val d2 = dx * dx + dy * dy
                if (d2 > r2) continue
                val v = grid[row + gx] + wt * falloff[d2]
                grid[row + gx] = v
                if (v > max) max = v
            }
        }
    }
    if (max > 0f) { val inv = 1f / max; for (i in grid.indices) grid[i] *= inv }
    return grid
}

/** 256 palette entries, built once; everything under [HEAT_THRESHOLD] is clear. */
private val LUT: IntArray by lazy { IntArray(256) { i -> val t = i / 255f; if (t < HEAT_THRESHOLD) 0 else heatColor(t) } }

/** One pixel per cell, to be scaled up to the view; values under [HEAT_THRESHOLD] are transparent. */
fun heatBitmap(grid: FloatArray, cols: Int, rows: Int): Bitmap {
    val px = IntArray(cols * rows) { i -> LUT[(grid[i].coerceIn(0f, 1f) * 255).roundToInt()] }
    return Bitmap.createBitmap(px, cols, rows, Bitmap.Config.ARGB_8888)
}
