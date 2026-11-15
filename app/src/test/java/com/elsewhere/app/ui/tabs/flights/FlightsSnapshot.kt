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
import com.elsewhere.app.share.LocalPrinting
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Poster
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

    // The saved card: the globe turned 90° east of its default and the open route kept, the drag hint and the Save menu gone.
    @Test fun printedGlobe() = paparazzi.snapshot {
        ElsewhereTheme {
            CompositionLocalProvider(LocalMotion provides false, LocalPrinting provides true) {
                Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true)) {
                    FlightsScreen(ui, Rotation(ui.centre.lng + 90.0, ui.centre.lat), onRotate = {}, selected = open, onSelect = {}, scroll = false)
                }
            }
        }
    }
}
