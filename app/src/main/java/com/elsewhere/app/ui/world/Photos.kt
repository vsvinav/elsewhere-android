package com.elsewhere.app.ui.world

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How pictures pay their way: `onGooglePhoto` tallies a Photos SKU once a Google image lands and
 * `commons` finds a free Wikimedia picture near a point. The default does neither, so snapshots stay offline.
 */
data class PhotoSources(
    val onGooglePhoto: () -> Unit = {},
    val commons: (suspend (lat: Double, lng: Double) -> String?)? = null,
)

val LocalPhotoSources = staticCompositionLocalOf { PhotoSources() }
