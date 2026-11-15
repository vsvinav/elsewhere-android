package com.elsewhere.app.ui.tabs.map

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.Fakes
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.data.Recording
import com.elsewhere.app.data.db.RawFixEntity
import com.elsewhere.app.map.PinLayers
import com.elsewhere.app.map.PinOpts
import com.elsewhere.app.record.Live
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class MapLiveTest {
    @get:Rule val rule = createComposeRule()
    private val now = 1_800_000_000L

    private fun live(control: Fakes.Control) = Live(control, { _, _ -> "Cambridge" }, { now }, { ZoneOffset.UTC }, 30_000)

    @Test fun `a recent fix while recording puts the stamp on the map and LIVE in the HUD`() {
        val control = Fakes.Control(Recording(true, now - 3600, 0L)).apply {
            fix = RawFixEntity(ts = now - 180, lat = 42.37, lng = -71.11, accuracyM = 12f, speedMps = null, altitude = null, activity = null, batteryPct = null)
        }
        var taps = 0
        rule.setContent {
            val stamp by live(control).stamps.collectAsStateWithLifecycle(null)
            ElsewhereTheme { MapScreen(PinLayers(SampleRecord.record), PinOpts(), MapActions(), live = stamp, onLive = { taps++ }) { m -> Box(m) } }
        }
        rule.onNodeWithText("LIVE").assertIsDisplayed()
        rule.onNodeWithTag(NOW_STAMP_TAG).assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Now · 07:57 · Cambridge · 3 min ago").assertIsDisplayed()
        assertEquals(1, taps)
    }

    @Test fun `off, the map shows neither`() {
        val control = Fakes.Control().apply {
            fix = RawFixEntity(ts = now, lat = 42.37, lng = -71.11, accuracyM = 12f, speedMps = null, altitude = null, activity = null, batteryPct = null)
        }
        rule.setContent {
            val stamp by live(control).stamps.collectAsStateWithLifecycle(null)
            ElsewhereTheme { MapScreen(PinLayers(SampleRecord.record), PinOpts(), MapActions(), live = stamp) { m -> Box(m) } }
        }
        rule.onNodeWithText("LIVE").assertDoesNotExist()
        rule.onNodeWithTag(NOW_STAMP_TAG).assertDoesNotExist()
    }
}
