package com.elsewhere.app.ui.tabs.tour

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.Fx
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.analytics.Stop
import com.elsewhere.app.data.FeatureCollection
import com.elsewhere.app.data.Path
import com.elsewhere.app.data.Pt
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.NightsBill
import com.elsewhere.app.ui.world.Poster
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.random.Random

class TourSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")
    private val ui = tourUi(SampleRecord.record, SampleRecord.record, SampleRecord.profile, null)

    // One phone screen per snapshot: the poster is taller than any golden can hold legibly.
    private fun sheet(content: @Composable () -> Unit) = paparazzi.snapshot {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { Column(Modifier.background(Poster.Stock)) { content() } } }
    }

    @Test fun hero() = sheet { TourScreen(ui, onGo = {}) }

    /**
     * A real-shaped record: a year of errands around one metro area, a handful of long flights across the
     * antimeridian and the equator, and the long city names that come with them. Land is the shipped atlas.
     */
    @Test fun dense() = sheet { TourScreen(ui.copy(tour = ui.tour.copy(stops = DenseRecord.stops), trail = DenseRecord.trail), onGo = {}) }
    @Test fun nights() = sheet { NightsBill(ui.nights!!, first = true) }
    @Test fun headline() = sheet { HeadlineBill(ui, onGo = {}) }
    @Test fun setlistAndResidency() = sheet { SetlistBill(ui); ResidencyBill(ui) }
    @Test fun recordsAndBackstage() = sheet { RecordsBill(ui); BackstageBill(ui) }
}

private object DenseRecord {
    private val land = File("src/main/assets/land.json").takeIf { it.exists() }?.let { FeatureCollection.fromJson(it.readText()) }
    private val rnd = Random(7)
    private fun stop(city: String, cc: String, lat: Double, lng: Double, nights: Int) = Stop(city, cc, nights, nights, 0, 0, lat, lng)

    // Forty-odd Bay Area suburbs within a degree of one another, then the far ends of the year.
    private val metro = (0 until 40).map { i ->
        val lat = 37.77 + (rnd.nextDouble() - 0.5) * 0.9; val lng = -122.42 + (rnd.nextDouble() - 0.5) * 1.1
        stop(listOf("San Francisco", "Oakland", "Berkeley", "South San Francisco", "Redwood City", "Palo Alto", "Mountain View", "Walnut Creek", "Half Moon Bay", "Pleasanton")[i % 10] + if (i >= 10) " $i" else "", "US", lat, lng, 60 - i)
    }
    val stops = metro + listOf(
        stop("Tokyo", "JP", 35.68, 139.69, 9), stop("Sydney", "AU", -33.87, 151.21, 6), stop("Ushuaia", "AR", -54.80, -68.30, 4),
        stop("Reykjavík", "IS", 64.15, -21.94, 3), stop("Saint-Jean-Cap-Ferrat", "FR", 43.69, 7.33, 2),
    )

    private fun walk(lat0: Double, lng0: Double, n: Int, t0: Long): Path {
        var lat = lat0; var lng = lng0
        val pts = (0 until n).map { i -> lat += (rnd.nextDouble() - 0.5) * 0.02; lng += (rnd.nextDouble() - 0.5) * 0.02; Pt(lat, lng, t0 + i * 60L) }
        return Path(t0, t0 + n * 60L, pts)
    }

    private fun fly(a: Stop, b: Stop, km: Double) = Fx.act(0, 8 * Fx.H, "FLYING", km * 1000, a.lng, a.lat, b.lng, b.lat)

    // A road trip south and east: San Francisco, Los Angeles, Phoenix, Houston, Miami, as a thousand fixes.
    private val roadTrip = listOf(37.77 to -122.42, 34.05 to -118.24, 33.45 to -112.07, 29.76 to -95.37, 25.76 to -80.19).zipWithNext().flatMapIndexed { i, (a, b) ->
        (0 until 250).map { j -> val t = j / 250.0; Pt(a.first + (b.first - a.first) * t + (rnd.nextDouble() - 0.5) * 0.3, a.second + (b.second - a.second) * t, (400 + i * 250 + j) * 60L) }
    }.let { Path(0, it.size * 60L, it) }

    private val record = Fx.record(
        strokes = metro.mapIndexed { i, s -> walk(s.lat, s.lng, 120, i * Fx.D) } + stops.drop(40).map { walk(it.lat, it.lng, 40, 0) } + roadTrip,
        activities = stops.drop(40).flatMap { far -> listOf(fly(metro[0], far, 9000.0), fly(far, metro[0], 9000.0)) },
    )
    val trail = com.elsewhere.app.ui.world.trailLayout(record, stops, land)
}
