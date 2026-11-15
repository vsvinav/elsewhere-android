package com.elsewhere.app.ui.tabs.facts

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.analytics.passport
import com.elsewhere.app.ui.nav.Door
import com.elsewhere.app.ui.nav.LocalOpenDoor
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Mirrors web/src/tabs/Facts/Passport.test.tsx.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class PassportBookletTest {
    @get:Rule val rule = createComposeRule()
    private val pass = passport(SampleRecord.record)
    private val opened = ArrayList<Door>()
    private var saved: Set<String>? = null

    private fun show(seen: Set<String>?) {
        rule.setContent {
            ElsewhereTheme {
                CompositionLocalProvider(LocalOpenDoor provides { opened += it }, LocalMotion provides false) {
                    PassportBooklet(pass, seen, onSeen = { saved = it })
                }
            }
        }
    }

    @Test fun `prints a page per country and one of milestones, with the stamped count`() {
        show(emptySet())
        assertEquals(listOf("PT", "JP", "US", "MILESTONES"), pass.pages.map { it.title })
        rule.onNodeWithContentDescription("PT, page 1 of 4").assertIsDisplayed()
        rule.onNodeWithText("01 · 04").assertIsDisplayed()
        rule.onNodeWithText("${pass.earnedIds.size} / ${pass.total}").assertIsDisplayed()
    }

    @Test fun `an earned stamp carries its date and opens the beds through a places door`() {
        show(emptySet())
        val lisbon = pass.pages[0].stamps[0]
        rule.onNodeWithContentDescription("Open on the map: Lisbon").assertIsDisplayed().assert(hasText(date(lisbon.earnedAt!!))).assertHasClickAction().performClick()
        rule.waitForIdle()
        assertEquals(listOf<Door>(Door.Places(lisbon.door!!.let { (it as Door.Places).placeIds }, "Lisbon")), opened)
    }

    @Test fun `an unearned stamp is a ghost outline that opens nothing, and the arrows turn the pages`() {
        show(emptySet())
        rule.onNodeWithContentDescription("Previous page").assertIsNotEnabled()
        repeat(3) { rule.onNodeWithContentDescription("Next page").performClick(); rule.waitForIdle() }
        rule.onNodeWithText("04 · 04").assertIsDisplayed()
        rule.onNodeWithContentDescription("Next page").assertIsNotEnabled()
        rule.onNodeWithContentDescription("MILESTONES, page 4 of 4").assertIsDisplayed()
        rule.onNodeWithContentDescription("1,000 NIGHTS AWAY: not yet").assertIsDisplayed().assertIsNotEnabled().performClick()
        rule.waitForIdle()
        assertEquals(emptyList<Door>(), opened)
        rule.onNodeWithContentDescription("Open on the map: FIRST NIGHT AWAY").assertIsDisplayed()
        val ms = pass.pages[3]
        rule.onNodeWithText("${ms.earned} / ${ms.stamps.size} earned", ignoreCase = true).assertIsDisplayed()
    }

    @Test fun `the first open records every earned stamp, and a new earn is recorded again`() {
        show(null)
        rule.waitForIdle()
        assertEquals(pass.earnedIds.toSet(), saved)
    }

    @Test fun `a booklet already seen in full records nothing`() {
        show(pass.earnedIds.toSet())
        rule.waitForIdle()
        assertNull(saved)
    }

    @Test fun `a stamp earned since the last open is recorded`() {
        show(pass.earnedIds.filter { it != "city:Lisbon" }.toSet())
        rule.waitForIdle()
        assertEquals(pass.earnedIds.toSet(), saved)
    }
}
