package com.elsewhere.app.map

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.exp
import kotlin.math.pow
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
    val xy = FloatArray(points.size * 2); val wt = FloatArray(points.size)
    for (i in points.indices) { xy[2 * i] = points[i].x; xy[2 * i + 1] = points[i].y; wt[i] = weights[i].toFloat() }
    val out = FloatArray(maxOf(1, w / cell) * maxOf(1, h / cell))
    heatGridInto(xy, wt, points.size, w, h, cell, radiusPx, out)
    return out
}

/** [heatGrid] over packed `x0, y0, x1, y1…` pairs (the first `n` points), written into `out` of `(w/cell) × (h/cell)` cells. */
internal fun heatGridInto(xy: FloatArray, weights: FloatArray, n: Int, w: Int, h: Int, cell: Int, radiusPx: Float, out: FloatArray) {
    val cols = maxOf(1, w / cell); val rows = maxOf(1, h / cell)
    out.fill(0f, 0, cols * rows)
    if (n == 0) return
    val r = radiusPx / cell
    val reach = kotlin.math.ceil(r).toInt()
    val r2 = r * r
    // Falls to ~e⁻² at the radius; one row of the kernel is reused across every point.
    val sigma2 = r2 / 4f
    val falloff = FloatArray(reach * reach * 2 + 1).also { for (d2 in it.indices) it[d2] = if (d2 <= r2) exp(-d2 / sigma2) else 0f }
    var max = 0f
    for (p in 0 until n) {
        val cx = xy[2 * p] / cell; val cy = xy[2 * p + 1] / cell
        val ix = cx.toInt(); val iy = cy.toInt()
        if (ix < -reach || iy < -reach || ix >= cols + reach || iy >= rows + reach) continue
        val wt = weights[p]
        for (gy in maxOf(0, iy - reach)..minOf(rows - 1, iy + reach)) {
            val dy = gy - iy
            val row = gy * cols
            for (gx in maxOf(0, ix - reach)..minOf(cols - 1, ix + reach)) {
                val dx = gx - ix
                val d2 = dx * dx + dy * dy
                if (d2 > r2) continue
                val v = out[row + gx] + wt * falloff[d2]
                out[row + gx] = v
                if (v > max) max = v
            }
        }
    }
    if (max > 0f) { val inv = 1f / max; for (i in 0 until cols * rows) out[i] *= inv }
}

/** 256 palette entries, built once; everything under [HEAT_THRESHOLD] is clear. */
private val LUT: IntArray by lazy { IntArray(256) { i -> val t = i / 255f; if (t < HEAT_THRESHOLD) 0 else heatColor(t) } }

/** One pixel per cell, to be scaled up to the view; values under [HEAT_THRESHOLD] are transparent. */
fun heatBitmap(grid: FloatArray, cols: Int, rows: Int): Bitmap =
    Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888).also { heatBitmapInto(grid, IntArray(cols * rows), it) }

/** [heatBitmap] into an existing `cols × rows` bitmap through a scratch `px` of the same size. */
internal fun heatBitmapInto(grid: FloatArray, px: IntArray, into: Bitmap) {
    val n = into.width * into.height
    for (i in 0 until n) px[i] = LUT[(grid[i].coerceIn(0f, 1f) * 255).roundToInt()]
    into.setPixels(px, 0, into.width, 0, 0, into.width, into.height)
}

internal const val HEAT_CELL_PX = 8

/**
 * What the heat bitmap depends on: the view and the stops in it. Same key, same picture; `dots` is
 * [PinLayers.shown]'s own cached instance for the filter, so identity is the comparison.
 */
internal class HeatKey(val cam: Cam, val dots: List<Dot>, val w: Int, val h: Int) {
    override fun equals(other: Any?) = other is HeatKey && other.cam == cam && other.dots === dots && other.w == w && other.h == h
    override fun hashCode() = cam.hashCode() * 31 + System.identityHashCode(dots)
}

/** Screen position of a point under a camera over a `w × h` px view; pure maths, see [Projector]. */
internal fun interface HeatProjector { fun at(lat: Double, lng: Double): Offset }

/**
 * The heat bitmap for the last key, regridded only when the camera, the filter or the view size change: a pan costs
 * one splat per frame of movement, a redraw of labels or the open pin none. Both hosts are north-up, so dots are
 * placed by the same Mercator maths as [viewBounds] rather than a call into the SDK each, and only after a plain
 * compare has dropped everything further off screen than the kernel reaches. Buffers and the bitmap are reused.
 */
internal class HeatCache(
    private val projector: (Cam, Int, Int, Float) -> HeatProjector = { cam, w, h, d -> Projector(cam, w, h, d).let { p -> HeatProjector(p::at) } },
) {
    private var key: HeatKey? = null
    private var image: ImageBitmap? = null
    private var bitmap: Bitmap? = null
    private var px = IntArray(0)
    private var grid = FloatArray(0)
    private var xy = FloatArray(0)
    private var wt = FloatArray(0)
    /** How many times a bitmap was built; for tests. */
    var regrids = 0
        private set

    fun image(k: HeatKey, density: Float): ImageBitmap? {
        if (k.w <= 0 || k.h <= 0) return null
        if (k == key) return image
        val n = cull(k, density)
        val cols = maxOf(1, k.w / HEAT_CELL_PX); val rows = maxOf(1, k.h / HEAT_CELL_PX)
        if (grid.size < cols * rows) { grid = FloatArray(cols * rows); px = IntArray(cols * rows) }
        heatGridInto(xy, wt, n, k.w, k.h, HEAT_CELL_PX, HEAT_RADIUS_PX * density, grid)
        val bmp = bitmap?.takeIf { it.width == cols && it.height == rows }
            ?: Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888).also { bitmap = it; image = it.asImageBitmap() }
        heatBitmapInto(grid, px, bmp)
        key = k; regrids++
        return image
    }

    /** Fills `xy`/`wt` with the dots within a kernel radius of the view; returns how many. */
    private fun cull(k: HeatKey, density: Float): Int {
        val dots = k.dots
        if (xy.size < dots.size * 2) { xy = FloatArray(dots.size * 2); wt = FloatArray(dots.size) }
        val proj = projector(k.cam, k.w, k.h, density)
        val view = viewBounds(k.cam, (k.w / density).roundToInt(), (k.h / density).roundToInt())
        // Degrees of longitude the kernel reaches past the edge; at any latitude a degree of latitude spans at least
        // as many pixels as one of longitude, so the same pad is safe on both axes.
        val pad = HEAT_RADIUS_PX / (256.0 * 2.0.pow(k.cam.zoom.toDouble())) * 360
        val s = view.swLat - pad; val nn = view.neLat + pad
        val wl = view.swLng - pad; val el = view.neLng + pad
        var n = 0
        for (d in dots) {
            val lat = d.lat
            if (lat < s || lat > nn) continue
            val lng = d.lng
            if (!(lng in wl..el || lng + 360 in wl..el || lng - 360 in wl..el)) continue
            val o = proj.at(lat, lng)
            xy[2 * n] = o.x; xy[2 * n + 1] = o.y; wt[n] = heatWeight(d).toFloat(); n++
        }
        return n
    }
}
