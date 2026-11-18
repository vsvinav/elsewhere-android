package com.elsewhere.app.ui.tabs.replay

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.Fx
import com.elsewhere.app.analytics.Grain
import com.elsewhere.app.analytics.Rail
import com.elsewhere.app.data.Home
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Role
import com.elsewhere.app.data.Trip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReplayHudTest {
    @get:Rule val rule = createComposeRule()

    private val range = Fx.utc(2019, 0)..Fx.utc(2024, 0)
    private val at = Fx.utc(2019, 2, 14) + 12 * Fx.H
    private val info = hudInfo(Fx.record(range = range), at)

    @Composable
    private fun Hud(
        grain: Grain = Grain.DAY, allowed: List<Grain> = Grain.entries, playing: Boolean = true, scrubbing: Boolean = false,
        onGrain: (Grain) -> Unit = {},
    ) = ReplayHud(
        hudInfo(trips, at, grain, grainIndex(trips)), range, at.toDouble(), playing, 1.0, true, grain, allowed, scrubbing, Rail(12480.0, 23, 61, 9, "…and that's your 25th city"),
        onPlay = {}, onSpeed = {}, onScrub = {}, onFollow = {}, onGrain = onGrain, onScrubbing = {},
    )

    @Test fun `the rail prints the four figures and the milestone`() {
        rule.setContent { Rail(Rail(12480.0, 23, 61, 9, "…and that's your 25th city"), visible = true) }
        rule.onNodeWithText("KM SO FAR").assertExists(); rule.onNodeWithText("12,480").assertExists()
        rule.onNodeWithText("23").assertExists(); rule.onNodeWithText("61").assertExists(); rule.onNodeWithText("9").assertExists()
        rule.onNodeWithText("…and that's your 25th city").assertExists()
    }

    @Test fun `no home means a dash for nights away`() {
        rule.setContent { Rail(Rail(0.0, 0, 0, 0, null), visible = true, noHome = true) }
        rule.onNodeWithText("—").assertExists()
    }

    @Test fun `a chip picks its grain and a disabled one does nothing`() {
        var g: Grain? = null
        rule.setContent { Hud(grain = Grain.MONTH, allowed = listOf(Grain.MONTH, Grain.DAY), onGrain = { g = it }) }
        rule.onNodeWithText("DAY").performClick(); assertEquals(Grain.DAY, g)
        g = null
        rule.onNodeWithText("YEAR").assertIsNotEnabled().performClick(); assertNull(g)
    }

    @Test fun `the headline names the playhead at the grain`() {
        assertEquals("2019", headline(Grain.YEAR, at, 0))
        assertEquals("MARCH 2019", headline(Grain.MONTH, at, 0))
        assertEquals("THU 14 MAR 2019", headline(Grain.DAY, at, 0))
        rule.setContent { Hud(grain = Grain.MONTH) }
        rule.onNodeWithText("MARCH 2019").assertExists()
    }

    private val trips = Fx.record(
        range = range,
        trips = listOf(
            Trip(Fx.utc(2019, 1, 10), Fx.utc(2019, 1, 20), 10, 500.0, emptyMap(), listOf("Porto"), null),
            Trip(Fx.utc(2019, 2, 12), Fx.utc(2019, 2, 16), 4, 900.0, emptyMap(), listOf("Madrid"), null),
            Trip(Fx.utc(2019, 5, 1), Fx.utc(2019, 5, 9), 8, 2000.0, emptyMap(), listOf("Rome"), null),
        ),
        visits = listOf(
            Fx.visit(Fx.utc(2019, 1, 10) + 20 * Fx.H, Fx.utc(2019, 1, 11) + 8 * Fx.H, "porto", lat = 41.1, lng = -8.6),
            Fx.visit(Fx.utc(2019, 2, 12) + 20 * Fx.H, Fx.utc(2019, 2, 14) + 8 * Fx.H, "madrid", lat = 40.4, lng = -3.7),
            Fx.visit(Fx.utc(2019, 2, 15), Fx.utc(2019, 2, 15) + 2 * Fx.H, "toledo", lat = 39.9, lng = -4.0),
            Fx.visit(Fx.utc(2019, 5, 1) + 20 * Fx.H, Fx.utc(2019, 5, 2) + 8 * Fx.H, "rome", lat = 41.9, lng = 12.5),
        ),
        places = mapOf(
            "porto" to Fx.place(city = "Porto", cc = "PT", lat = 41.1, lng = -8.6), "madrid" to Fx.place(city = "Madrid", cc = "ES", lat = 40.4, lng = -3.7),
            "toledo" to Fx.place(city = "Toledo", cc = "ES", lat = 39.9, lng = -4.0), "rome" to Fx.place(city = "Rome", cc = "IT", lat = 41.9, lng = 12.5),
        ),
        home = LatLng(38.72, -9.14),
    )

    @Test fun `the year counts trips begun and countries first seen by the playhead`() {
        val i = hudInfo(trips, at, Grain.YEAR, grainIndex(trips))
        assertEquals("SO FAR", i.lab); assertEquals("2 trips", i.where); assertEquals("2 countries", i.sub)
        assertEquals("1 trip", hudInfo(trips, Fx.utc(2019, 2, 1), Grain.YEAR, grainIndex(trips)).where)
        assertEquals("1 country", hudInfo(trips, Fx.utc(2019, 2, 1), Grain.YEAR, grainIndex(trips)).sub)
    }

    @Test fun `the month counts its cities and nights away`() {
        val i = hudInfo(trips, at, Grain.MONTH, grainIndex(trips))
        assertEquals("THIS MONTH", i.lab); assertEquals("2 cities", i.where); assertEquals("2 nights", i.sub)
        assertEquals("1 city", hudInfo(trips, Fx.utc(2019, 5, 20), Grain.MONTH, grainIndex(trips)).where)
    }

    @Test fun `the day reads as before`() {
        val i = hudInfo(trips, at, Grain.DAY, grainIndex(trips))
        assertEquals("NOW", i.lab); assertEquals("Other", i.where); assertEquals("in motion", i.sub)
        assertEquals("AT", hudInfo(trips, Fx.utc(2019, 2, 15) + Fx.H, Grain.DAY, grainIndex(trips)).lab)
    }

    @Test fun `the where line follows the grain`() {
        var grain by mutableStateOf(Grain.YEAR)
        rule.setContent { Hud(grain = grain) }
        rule.onNodeWithText("SO FAR").assertExists(); rule.onNodeWithText("2 TRIPS").assertExists(); rule.onNodeWithText("2 countries").assertExists()
        grain = Grain.MONTH
        rule.onNodeWithText("THIS MONTH").assertExists(); rule.onNodeWithText("2 CITIES").assertExists()
        grain = Grain.DAY
        rule.onNodeWithText("NOW").assertExists()
    }

    @Test fun `no home on record means no home, whatever the beds say`() {
        assertTrue(noHome(Fx.record()))
        assertFalse(noHome(Fx.record(places = mapOf("h" to Fx.place(role = Role.HOME)))))
        assertFalse(noHome(Fx.record().let { it.copy(profile = it.profile.copy(homes = listOf(Home(0.0, 0.0, "x", "PT", 0L, null)))) }))
    }

    @Test fun `the rail shows only while a finger holds the strip`() {
        rule.setContent { Hud(playing = true, scrubbing = false) }
        rule.onNodeWithTag("rail").assertDoesNotExist()
    }

    @Test fun `the rail prints while scrubbing`() {
        rule.setContent { Hud(playing = false, scrubbing = true) }
        rule.onNodeWithTag("rail").assertExists(); rule.onNodeWithText("12,480").assertExists()
    }
}
