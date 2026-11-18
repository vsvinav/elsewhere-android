package com.elsewhere.app.map

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Pure Web-Mercator projection of a north-up camera over a viewport, so a frame of the replay projects thousands of
 * points without a JNI call each, and against the very camera the loop just set rather than whatever the SDK has
 * rendered so far. Both SDKs draw a 256-logical-pixel tile per zoom level, so `density` turns that into physical px.
 */
class Projector(cam: Cam, widthPx: Int, heightPx: Int, density: Float = 1f) {
    /** The whole world's width in px at this zoom: a run of points that jumps further than half of it has crossed the date line. */
    val world = 256.0 * 2.0.pow(cam.zoom.toDouble()) * density
    private val cx = (cam.lng + 180) / 360 * world
    private val cy = (0.5 - mercRad(cam.lat) / (2 * PI)) * world
    private val ox = widthPx / 2.0 - cx
    private val oy = heightPx / 2.0 - cy

    /** The box on view, in the same terms as [viewBounds]; longitudes may run past ±180. */
    val bounds: Bounds = viewBounds(cam, (widthPx / density).roundToInt(), (heightPx / density).roundToInt())

    fun at(lat: Double, lng: Double): Offset = Offset(x(lng), y(lat))

    fun x(lng: Double): Float {
        var x = (lng + 180) / 360 * world + ox
        // Keep the point on the near side of the date line.
        val half = world / 2
        val sx = x - ox - cx
        if (sx > half) x -= world else if (sx < -half) x += world
        return x.toFloat()
    }

    fun y(lat: Double): Float {
        val l = lat.coerceIn(-MAX_LAT, MAX_LAT) * PI / 180
        return ((0.5 - ln(tan(PI / 4 + l / 2)) / (2 * PI)) * world + oy).toFloat()
    }
}
