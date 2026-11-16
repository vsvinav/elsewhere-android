package com.elsewhere.app.ui.crew

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.crew.Grade
import com.elsewhere.app.crew.ShareLength
import com.elsewhere.app.data.Recording
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FriendsLayerTest {
    @get:Rule val rule = createComposeRule()
    private val now = CrewFixtures.NOW

    @Test fun `a mark goes stale after fifteen minutes and gone after a day`() {
        val fresh = CrewFixtures.fix(CrewFixtures.marta, 38.7, -9.1, "Lisbon", 60)
        val stale = CrewFixtures.fix(CrewFixtures.marta, 38.7, -9.1, "Lisbon", 16 * 60)
        val gone = CrewFixtures.fix(CrewFixtures.marta, 38.7, -9.1, "Lisbon", 25 * 3600)
        assertFalse(FriendMark.of(fresh, now).stale); assertFalse(fresh.gone(now))
        assertTrue(FriendMark.of(stale, now).stale); assertFalse(stale.gone(now))
        assertTrue(gone.gone(now))
        assertEquals("3 min ago", FriendMark.of(CrewFixtures.fix(CrewFixtures.marta, 38.7, -9.1, "Lisbon", 180), now).ageLabel)
    }

    @Test fun `rings draw only for friends whose ping just hit`() {
        val ui = CrewFixtures.ui()
        val marks = ui.fixes.values.map { FriendMark.of(it, now) }
        val at = mutableStateOf(marks.associate { it.id to Offset(200f, 300f) })
        rule.setContent {
            ElsewhereTheme { FriendStamps(FriendsUi(marks, layerOn = true, selected = null, hits = setOf(CrewFixtures.marta.deviceId), myCity = "Lisbon", crew = ui), at, onTap = {}) }
        }
        rule.onAllNodesWithTag(FRIEND_STAMP_TAG).assertCountEquals(3)
        rule.onAllNodesWithTag(SONAR_RING_TAG).assertCountEquals(1)
    }

    @Test fun `the bubble says when you share a city`() {
        val mark = FriendMark.of(CrewFixtures.fix(CrewFixtures.marta, 38.7, -9.1, "Lisbon", 180), now)
        rule.setContent { ElsewhereTheme { FriendBubble(mark, myCity = "Lisbon", onClose = {}) } }
        rule.onNodeWithText("YOU'RE BOTH IN LISBON").assertIsDisplayed()
    }

    @Test fun `sharing from a cold Record starts recording first`() {
        val calls = ArrayList<String>()
        val acts = object : CrewActions by CrewFixtures.NoOp {
            override fun startRecording() { calls += "record" }
            override fun startShare(friendIds: Set<String>, length: ShareLength) { calls += "share:${friendIds.sorted()}:$length" }
        }
        rule.setContent { ElsewhereTheme { ShareSheetBody(CrewFixtures.ui(recording = false), acts, onClose = {}) } }
        rule.onNodeWithText(SHARE_NEEDS_RECORD).assertIsDisplayed()
        rule.onAllNodesWithText("KENJI").assertCountEquals(0)
        rule.onNodeWithText("JULES").performClick()
        rule.onNodeWithText("START RECORD & SHARE").performClick()
        val with = listOf(CrewFixtures.marta, CrewFixtures.asta).map { it.deviceId }.sorted()
        assertEquals(listOf("record", "share:$with:HOUR"), calls)
    }
}
