package com.elsewhere.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.elsewhere.app.data.Viewport
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What a screen needs from whichever map draws underneath its overlays: the camera to redraw against, the
 * events the follow-cam and deck listen for, taps to hit-test, and a projection. Pins and trails are Compose
 * canvases rather than map layers, so MapLibre and Google's SDK are interchangeable behind this.
 */
interface MapHost {
    val camera: StateFlow<Cam>
    val events: SharedFlow<MapEvent>
    val taps: SharedFlow<Tap>
    /** What the camera shows once it has settled; null before the map has laid out. */
    val viewport: StateFlow<Viewport?>

    fun moveTo(cam: Cam, animateMs: Int = 0)
    fun fit(bounds: Bounds, paddingPx: Int)
    /** Where a point lands on screen, in px from [Content]'s top-left; null before the map has laid out. */
    fun project(lat: Double, lng: Double): Offset?

    @Composable
    fun Content(modifier: Modifier, basemap: Basemap, copyright: String?, overlays: @Composable () -> Unit)
}
