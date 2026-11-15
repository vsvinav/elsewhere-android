package com.elsewhere.app.ui.tabs.highlights

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.semantics.SemanticsProperties
import com.elsewhere.app.analytics.highlightFor
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.elsewhere.app.OnThisDay
import com.elsewhere.app.OnThisDay.at
import com.elsewhere.app.analytics.Answer
import com.elsewhere.app.analytics.FollowUpId
import com.elsewhere.app.analytics.MAX_DEPTH
import com.elsewhere.app.analytics.followUps
import com.elsewhere.app.OnThisDay.noHome
import com.elsewhere.app.ui.world.InkButton
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import com.elsewhere.app.data.Record
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Twin of web/src/tabs/highlights/highlights.test.tsx.

data class Open(val range: LongRange, val playhead: Long?, val placeIds: List<String>)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HighlightCardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val data = OnThisDay.data()

    private fun mount(today: String, d: Record = data, onKeyStep: (() -> Unit)? = null): MutableList<Open> {
        val calls = ArrayList<Open>()
        rule.setContent { ElsewhereTheme { HighlightCard(d, { r, p, ids -> calls.add(Open(r, p, ids)) }, today = today, onKeyStep = onKeyStep) } }
        return calls
    }
    private fun ask() { rule.onNodeWithText("ASK FOR MORE").performClick(); rule.waitForIdle() }
    private fun questions() = rule.onAllNodes(hasTestTag("hl-q")).fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].joinToString { t -> t.text } }
    private fun back() { rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }

    @Test fun `prints the headline, three facts as doors and the scoped map`() {
        val calls = mount("2025-11-14")
        rule.onNodeWithText("On this day, 2 years ago — you woke up in Kyoto.").assertIsDisplayed()
        rule.onNodeWithContentDescription("3 nights into a 7-night trip — open on the map").assertIsDisplayed()
        rule.onNodeWithContentDescription("Weather of your week — open on the map").assertIsDisplayed()
        rule.onNodeWithContentDescription("10,800 km from home — open on the map").performClick()
        assertEquals(listOf(Open(at(2023, 11, 14)..at(2023, 11, 15), at(2023, 11, 11, 23), listOf("kyoto-hotel"))), calls)
    }
    @Test fun `the map is a door`() {
        val calls = mount("2025-11-14")
        rule.onNodeWithContentDescription("Open this day on the map").performClick()
        assertEquals(1, calls.size)
        assertEquals(at(2023, 11, 11, 23), calls[0].playhead)
    }
    @Test fun `quiet day names the nearest highlight and shows it on request`() {
        mount("2025-07-20")
        rule.onNodeWithText("Nothing from a 20 July yet.").assertIsDisplayed()
        rule.onAllNodesWithText("ASK FOR MORE").assertCountEquals(0)
        rule.onNodeWithText("3 May 2025, Kyoto").performClick()
        rule.onNodeWithText("On this day, earlier this year — you woke up in Kyoto.").assertIsDisplayed()
        rule.onNodeWithText("Nothing from a 20 July yet — the nearest day with a story:").assertIsDisplayed()
    }
    @Test fun `record shorter than a year shows the farthest day with the start-date note`() {
        val d = data.copy(meta = data.meta.copy(range = at(2023, 11, 11, 8)..at(2024, 5, 1)))
        mount("2024-04-10", d)
        rule.onNodeWithText("Your record starts 11 November. Highlights begin on 11 November next year — meanwhile, here's your farthest day so far.").assertIsDisplayed()
        rule.onNodeWithText("landed in Paris", substring = true).assertIsDisplayed()
    }
    @Test fun `offers the key step when the city is unnamed`() {
        val d = data.copy(places = data.places + ("nyc-hotel" to data.places.getValue("nyc-hotel").copy(city = null)))
        var steps = 0
        mount("2028-02-29", d) { steps++ }
        rule.onNodeWithText("40.71°, -74.01°", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Add a Google Maps key").performClick()
        assertEquals(1, steps)
    }
    @Test fun `pick another day re-reads the record for that date`() {
        mount("2025-11-14")
        rule.onNodeWithText("ANOTHER DAY").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("NOVEMBER").assertIsDisplayed()
        rule.onNode(isSelectable() and hasText("11")).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("On this day, 2 years ago — you landed in Kyoto.").assertIsDisplayed()
    }

    @Test fun `opens a sheet listing only the answerable questions`() {
        mount("2025-11-14")
        rule.onAllNodes(hasContentDescription("Ask for more")).assertCountEquals(0)
        ask()
        rule.onNodeWithContentDescription("Ask for more").assertIsDisplayed()
        assertEquals(
            listOf("How long did I stay?", "How far from home?", "What else happened that trip?", "Show me the route", "How does this compare to other years?", "What did I do that day?", "Have I been back?", "Was this my farthest ever?"),
            questions(),
        )
        rule.onNodeWithContentDescription("Thread").assertIsDisplayed()
        rule.onNodeWithText("KYOTO").assertIsDisplayed()
    }
    @Test fun `prints answers as passes with their own doors and grows the breadcrumb`() {
        val calls = mount("2025-11-14")
        ask()
        rule.onNodeWithText("How long did I stay?").performClick()
        rule.onNodeWithText("6 nights").assertIsDisplayed()
        rule.onNodeWithText("12–17 Nov 2023").assertIsDisplayed()
        rule.onNodeWithText("THE STAY").assertIsDisplayed()
        assertTrue("How long did I stay?" !in questions())
        rule.onNodeWithText("OPEN ON THE MAP").performClick()
        assertEquals(1, calls.size)
        assertEquals(listOf("kyoto-hotel"), calls[0].placeIds)
        rule.onNodeWithText("What else happened that trip?").performScrollTo().performClick()
        rule.onNodeWithText("Osaka").assertIsDisplayed()
        rule.onNodeWithText("1 night").assertIsDisplayed()
        rule.onNodeWithText("THE TRIP").assertIsDisplayed()
    }
    @Test fun `stops at depth 4`() {
        mount("2025-11-15")
        ask()
        repeat(MAX_DEPTH) { rule.onAllNodes(hasTestTag("hl-q")).onFirst().performScrollTo().performClick(); rule.waitForIdle() }
        assertEquals(0, questions().size)
        rule.onNodeWithText("THAT'S AS FAR AS THIS THREAD GOES.").performScrollTo().assertIsDisplayed()
    }
    @Test fun `this time last year prints another highlight inside the sheet`() {
        val d = data.copy(daily = data.daily.map { if (it.date == "2022-11-15") it.copy(nNewPlaces = 0) else it })
        mount("2025-11-15", d)
        rule.onNodeWithText("Kyoto", substring = true).assertIsDisplayed()
        ask()
        rule.onNodeWithText("What was I doing this time last year?").performScrollTo().performClick()
        // Once as the thread's lede, once as the answer's title.
        rule.onAllNodesWithText("On this day, 3 years ago — you landed in Paris.").assertCountEquals(2)
        rule.onNodeWithText("PARIS").assertIsDisplayed()
        assertTrue("How long did I stay?" in questions())
    }
    @Test fun `back pops one answer, then closes`() {
        // The sheet's content is hosted directly: inside ModalBottomSheet back goes to the dialog window, out of the test's reach.
        var closed = 0
        rule.setContent { ElsewhereTheme { AskMoreContent(data, highlightFor(data, "2025-11-14")!!, "2025-11-14", { closed++ }, { _, _, _ -> }) } }
        rule.onNodeWithText("How long did I stay?").performClick()
        rule.onNodeWithText("6 nights").assertIsDisplayed()
        back()
        rule.onAllNodesWithText("6 nights").assertCountEquals(0)
        assertEquals(0, closed)
        back()
        assertEquals(1, closed)
    }
    @Test fun `compare years marks today's year and drops the km when no home is set`() {
        mount("2025-11-14")
        ask()
        rule.onNodeWithText("How does this compare to other years?").performScrollTo().performClick()
        rule.onNodeWithText("10,800 km").assertIsDisplayed()
        rule.onAllNodesWithText("NOW").assertCountEquals(0)
    }
    @Test fun `compare years drops the km when no home is set`() {
        val d = data.noHome()
        rule.setContent { ElsewhereTheme { AskMoreContent(d, highlightFor(d, "2024-11-14")!!, "2024-11-14", {}, { _, _, _ -> }) } }
        rule.onNodeWithText("How does this compare to other years?").performScrollTo().performClick()
        rule.onNodeWithText("NOW").assertIsDisplayed()
        rule.onAllNodesWithText(" km", substring = true).assertCountEquals(0)
        rule.onNodeWithText("Boston · Kyoto · Boston").assertIsDisplayed()
    }
    @Test fun `the route pass replays the trip on its own map over ten seconds`() {
        val calls = ArrayList<Open>()
        val d = data
        val route = followUps(d, highlightFor(d, "2025-11-14")!!, today = "2025-11-14").first { it.id == FollowUpId.ROUTE }.answer as Answer.Route
        lateinit var replay: ReplayState
        rule.setContent { ElsewhereTheme { Column { replay = rememberReplay(route.range); RouteFigure(d, route, 0, replay); InkButton("Open on the map", { calls.add(Open(route.range, route.playhead, route.placeIds)) }) } } }
        val r0 = route.range.first; val r1 = route.range.last
        assertEquals(null, replay.playhead)
        val play = rule.onNode(hasText("PLAY THIS ROUTE"))
        play.assertIsNotSelected()
        play.performClick()
        // The clock is paused once the replay has started, so the ten seconds are stepped by hand.
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("REPLAYING…").assertIsSelected()
        rule.mainClock.advanceTimeBy(5_000)
        val mid = replay.playhead!!
        assertTrue("$mid", kotlin.math.abs(mid - (r0 + (r1 - r0) / 2)) < (r1 - r0) / 20)
        rule.onNodeWithContentDescription("Playhead").assertTextEquals("15 NOV 2023")
        rule.mainClock.advanceTimeBy(6_000)
        assertEquals(r1, replay.playhead)
        rule.onNodeWithText("PLAY AGAIN").assertIsNotSelected()
        // The door to the real map survives beside the replay.
        rule.onNodeWithText("OPEN ON THE MAP").performClick()
        rule.mainClock.advanceTimeByFrame()
        assertEquals(r0..r1, calls[0].range)
    }
    @Test fun `the close button dismisses the sheet`() {
        mount("2025-11-14")
        ask()
        rule.onNodeWithContentDescription("Close").performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasContentDescription("Ask for more")).assertCountEquals(0)
    }
    @Test fun `copy helpers`() {
        assertEquals(listOf("earlier this year", "last year", "2 years ago"), listOf(0, 1, 2).map(::ago))
    }
}
