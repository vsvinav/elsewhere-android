package com.elsewhere.app.ui.tabs.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.Fakes
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.data.Recording
import com.elsewhere.app.data.Viewport
import com.elsewhere.app.data.db.RawFixEntity
import com.elsewhere.app.map.Basemap
import com.elsewhere.app.map.Bounds
import com.elsewhere.app.map.Cam
import com.elsewhere.app.map.MapEvent
import com.elsewhere.app.map.MapHost
import com.elsewhere.app.map.PinLayers
import com.elsewhere.app.map.PinOpts
import com.elsewhere.app.map.Tap
import com.elsewhere.app.record.Live
import com.elsewhere.app.record.NowStamp
import com.elsewhere.app.ui.world.ElsewhereTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
    private val stamp = NowStamp(42.37, -71.11, "07:57", "Cambridge", "3 min ago")

    private fun live(control: Fakes.Control) = Live(control, { _, _ -> "Cambridge" }, { now }, { ZoneOffset.UTC }, 30_000)

    /** A host whose projection is null until a viewport arrives, as the SDKs' are before layout. */
    private class Host : MapHost {
        override val camera = MutableStateFlow(Cam(42.0, -71.0, 12f))
        override val events = MutableSharedFlow<MapEvent>()
        override val taps = MutableSharedFlow<Tap>()
        override val viewport = MutableStateFlow<Viewport?>(null)
        override fun moveTo(cam: Cam, animateMs: Int) { camera.value = cam }
        override fun fit(bounds: Bounds, paddingPx: Int) {}
        override fun project(lat: Double, lng: Double): Offset? = viewport.value?.let { Offset(100f, 200f) }
        @Composable override fun Content(modifier: Modifier, basemap: Basemap, copyright: String?, overlays: @Composable () -> Unit) {}
    }

    @Test fun `a recent fix while recording puts the stamp on the map and LIVE in the HUD`() {
        val control = Fakes.Control(Recording(true, now - 3600, 0L)).apply {
            fix = RawFixEntity(ts = now - 180, lat = 42.37, lng = -71.11, accuracyM = 12f, speedMps = null, altitude = null, activity = null, batteryPct = null)
        }
        var taps = 0
        rule.setContent {
            val stamp by live(control).stamps.collectAsStateWithLifecycle(null)
            ElsewhereTheme { MapScreen(PinLayers(SampleRecord.record), PinOpts(), MapActions(), live = stamp, liveAt = mutableStateOf(Offset(200f, 300f)), onLive = { taps++ }) { m -> Box(m) } }
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

    @Test fun `the mark is placed nowhere until the host has projected the fix`() {
        val at = mutableStateOf<Offset?>(null)
        rule.setContent { ElsewhereTheme { Box(Modifier.fillMaxSize()) { NowStampMark(stamp, at, {}, Modifier.fillMaxSize()) } } }
        rule.onNodeWithTag(NOW_STAMP_TAG).assertIsNotDisplayed()
        at.value = Offset(150f, 250f)
        rule.onNodeWithTag(NOW_STAMP_TAG).assertIsDisplayed()
    }

    @Test fun `the projection arriving is enough to place the stamp, without a camera change`() = runTest {
        val host = Host()
        val seen = ArrayList<Offset?>()
        liveOffsets(host, stamp).onEach { seen += it }.launchIn(backgroundScope)
        runCurrent()
        assertEquals(listOf<Offset?>(null), seen)
        host.viewport.value = Viewport(12, 43.0, 41.0, -70.0, -72.0)
        runCurrent()
        assertEquals(listOf(null, Offset(100f, 200f)), seen)
        host.camera.value = Cam(42.5, -71.0, 13f)
        runCurrent()
        assertEquals(3, seen.size)
    }

    @Test fun `the pulse never recomposes the mark`() {
        var compositions = 0
        val at: State<Offset?> = mutableStateOf(Offset(150f, 250f))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ElsewhereTheme {
                Box(Modifier.fillMaxSize()) { NowStampMark(stamp, at, {}, Modifier.fillMaxSize(), onComposed = { compositions++ }) }
            }
        }
        rule.waitForIdle()
        val before = compositions
        repeat(30) { rule.mainClock.advanceTimeByFrame() }
        rule.mainClock.advanceTimeBy(1_300)
        rule.waitForIdle()
        assertEquals(1, before)
        assertEquals(before, compositions)
    }
}
