package com.elsewhere.app.ui.tabs.replay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class ScrubTest {
    @Test fun `fineness is 1 on the strip and falls by 4_5 per strip height above, floored at a twentieth`() {
        assertEquals(1.0, fineness(0f, 56f), 1e-9); assertEquals(1.0, fineness(30f, 56f), 1e-9)
        assertEquals(1 / 4.5, fineness(-56f, 56f), 1e-6); assertEquals(1 / 20.0, fineness(-112f, 56f), 1e-6); assertEquals(1 / 20.0, fineness(-500f, 56f), 1e-6)
    }

    @Test fun `scrubDelta moves time by dx times span per px times fineness, clamped`() {
        val r = 0L..10_000L
        assertEquals(2000.0, scrubDelta(1000.0, 10f, 0f, 100f, 56f, r), 1e-6)
        assertEquals(1050.0, scrubDelta(1000.0, 10f, -112f, 100f, 56f, r), 1e-6)
        assertEquals(10_000.0, scrubDelta(9_900.0, 50f, 0f, 100f, 56f, r), 1e-6)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrubGestureTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `dragging pauses, scrubs, and lets go a moment later`() {
        var head = 0.0; var scrubbing = false; var paused = false
        rule.setContent { Scrub(0L..10_000L, head, onScrub = { head = it }, onScrubbing = { scrubbing = it; if (it) paused = true }, onGrainStep = {}) }
        rule.onNodeWithTag("scrub").performTouchInput { down(Offset(width / 2f, height / 2f)); moveBy(Offset(width / 10f, 0f)) }
        rule.waitForIdle()
        assertTrue(paused); assertTrue(scrubbing); assertEquals(6_000.0, head, 60.0)
        rule.onNodeWithTag("scrub").performTouchInput { up() }
        rule.waitForIdle(); assertTrue(scrubbing)
        rule.mainClock.advanceTimeBy(700); rule.waitForIdle()
        assertFalse(scrubbing)
    }

    @Test fun `a finger lifted two strip heights up moves time a twentieth as far`() {
        var head = 0.0
        rule.setContent { Scrub(0L..10_000L, head, onScrub = { head = it }, onScrubbing = {}, onGrainStep = {}) }
        rule.onNodeWithTag("scrub").performTouchInput {
            down(Offset(width / 2f, height / 2f)); moveBy(Offset(0f, -height / 2f - 2 * height)); moveBy(Offset(width / 10f, 0f))
        }
        rule.waitForIdle()
        assertEquals(5_050.0, head, 60.0)
    }

    @Test fun `pinching the strip steps the grain once, coarser when the fingers close`() {
        val steps = ArrayList<Int>(); var moved = 0
        rule.setContent { Scrub(0L..10_000L, 5_000.0, onScrub = { moved++ }, onScrubbing = {}, onGrainStep = { steps += it }) }
        rule.onNodeWithTag("scrub").performTouchInput {
            down(0, Offset(width * 0.3f, height / 2f)); down(1, Offset(width * 0.7f, height / 2f))
            moveTo(0, Offset(width * 0.45f, height / 2f)); moveTo(1, Offset(width * 0.55f, height / 2f))
            moveTo(0, Offset(width * 0.48f, height / 2f)); moveTo(1, Offset(width * 0.52f, height / 2f))
            up(0); up(1)
        }
        rule.waitForIdle()
        assertEquals(listOf(-1), steps)
        assertEquals(1, moved)
    }
}
