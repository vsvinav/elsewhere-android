package com.elsewhere.app.ui.tabs.flights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.analytics.Airports
import com.elsewhere.app.data.FeatureCollection
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.elsewhere.app.share.ShareCard
import com.elsewhere.app.share.ShareFormat
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.Tone
import org.junit.Rule
import org.junit.Test
import java.io.File

/** The Flights page over the sample record's four legs: the globe faces the Atlantic, Boston–Tokyo is open. */
class FlightsSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    private val airports = Airports.fromJson(javaClass.classLoader.getResource("airports.json")!!.readText())
    // Land is only shipped as an app asset; unit tests run from the module directory, so read it from there.
    private val land = File("src/main/assets/land.json").takeIf { it.exists() }?.let { FeatureCollection.fromJson(it.readText()) }

    private val ui by lazy { flightsUi(SampleRecord.record, SampleRecord.profile, airports, land) }
    private val open get() = ui.byN.indexOfFirst { it.km > 8_000 }.takeIf { it >= 0 }

    // Unbounded height, as a scrolling sheet would give: bills past the fold are laid out below it, not squeezed over it.
    private fun sheet(content: @Composable () -> Unit) = paparazzi.snapshot {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { Column(Modifier.background(Poster.Stock).wrapContentHeight(Alignment.Top, unbounded = true)) { content() } } }
    }

    @Test fun globe() = sheet { FlightsScreen(ui, ui.centre, onRotate = {}, selected = open, onSelect = {}, scroll = false) }
    @Test fun ledger() = sheet { RoutesBill(ui, open) {}; AirportsBill(ui) }
    @Test fun from() = sheet { FromBill(ui, 0) {} }

    // The saved card, on the story sheet it is shared from: the globe turned 90° east of its default and the open route
    // kept, the drag hint and the Save menu gone. One output pixel per golden pixel.
    @Test fun printedGlobe() {
        paparazzi.unsafeUpdateConfig(
            deviceConfig = DeviceConfig.PIXEL_6.copy(screenWidth = 1080, screenHeight = 1920, density = Density.MEDIUM, softButtons = false),
            renderingMode = RenderingMode.SHRINK,
        )
        paparazzi.snapshot {
            ElsewhereTheme {
                CompositionLocalProvider(LocalMotion provides false) {
                    ShareCard(ShareFormat.STORY, paper = Tone.INK.fill, ink = Tone.INK.text) {
                        HeroBill(ui, Rotation(ui.centre.lng + 90.0, ui.centre.lat), onRotate = {}, selected = open, onSelect = {})
                    }
                }
            }
        }
    }
}
