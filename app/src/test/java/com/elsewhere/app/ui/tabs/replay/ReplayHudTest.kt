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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        info, range, at.toDouble(), playing, 1.0, true, grain, allowed, scrubbing, Rail(12480.0, 23, 61, 9, "…and that's your 25th city"),
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

    @Test fun `the rail shows only while a finger holds the strip`() {
        rule.setContent { Hud(playing = true, scrubbing = false) }
        rule.onNodeWithTag("rail").assertDoesNotExist()
    }

    @Test fun `the rail prints while scrubbing`() {
        rule.setContent { Hud(playing = false, scrubbing = true) }
        rule.onNodeWithTag("rail").assertExists(); rule.onNodeWithText("12,480").assertExists()
    }
}
