package com.elsewhere.app.share

import androidx.compose.runtime.CompositionLocalProvider
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.ui.tabs.tour.HeadlineBill
import com.elsewhere.app.ui.tabs.tour.TourHero
import com.elsewhere.app.ui.tabs.tour.tourUi
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Tone
import org.junit.Rule
import org.junit.Test

/** The three sheets a card can print on, with the Tour hero as the sample. One output pixel per golden pixel. */
class ShareCardSnapshot {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(screenWidth = 1080, screenHeight = 1920, density = Density.MEDIUM, softButtons = false),
        theme = "android:Theme.Material.Light.NoActionBar", renderingMode = RenderingMode.SHRINK,
    )
    private val ui = tourUi(SampleRecord.record, SampleRecord.record, SampleRecord.profile, null)

    private fun card(format: ShareFormat) = paparazzi.snapshot {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { ShareCard(format) { TourHero(ui, onGo = {}) } } }
    }

    @Test fun story() = card(ShareFormat.STORY)

    /** An ink bill brings its own paper: the sheet and the brand line print in the bill's colours, as on the web. */
    @Test fun inkStory() = paparazzi.snapshot {
        ElsewhereTheme {
            CompositionLocalProvider(LocalMotion provides false) {
                ShareCard(ShareFormat.STORY, paper = Tone.INK.fill, ink = Tone.INK.text) { HeadlineBill(ui, onGo = {}) }
            }
        }
    }
    @Test fun post() = card(ShareFormat.POST)

    @Test fun free() {
        // A free card is as tall as its content; let the sheet grow past the device.
        paparazzi.unsafeUpdateConfig(renderingMode = RenderingMode.V_SCROLL)
        card(ShareFormat.FREE)
    }
}
