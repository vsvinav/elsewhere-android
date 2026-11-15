package com.elsewhere.app.ui.world

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.data.db.PhotoEntity
import org.junit.Rule
import org.junit.Test
import java.util.TimeZone

/** A snapped photo's card: a mustard square stands in for the picture. */
class PhotoCardSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    private fun solid(): ImageBitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFE9B531.toInt()) }.asImageBitmap()

    @Test fun card() {
        val tz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            paparazzi.snapshot {
                val photo = PhotoEntity("ph", 1763208000000L, 38.7134, -9.1309, "lis-chafariz-xyz", "Lisbon", "Late light on the tiles", "ph.jpg")
                ElsewhereTheme {
                    CompositionLocalProvider(LocalMotion provides false) {
                        PhotoCard(photo, solid(), "Chafariz d'El Rei", "Alfama, Lisboa", Modifier.padding(16.dp))
                    }
                }
            }
        } finally { TimeZone.setDefault(tz) }
    }
}
