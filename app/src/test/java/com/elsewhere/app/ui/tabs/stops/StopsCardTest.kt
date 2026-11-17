package com.elsewhere.app.ui.tabs.stops

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.elsewhere.app.Fx
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Role
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Twin of the card cases in web/src/tabs/Places/Places.test.tsx: the address takes the sub-line, and the name of an unnamed stop.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class StopsCardTest {
    @get:Rule val rule = createComposeRule()
    private val seen = ArrayList<String>()

    private fun show(vararg places: Pair<String, Place>, addresses: Map<String, String?> = emptyMap()) {
        val ui = stopsUi(Fx.record(places = places.toMap()), StopsFilter())
        rule.setContent { ElsewhereTheme { StopsScreen(ui, {}, scroll = false, onSeen = { seen += it }, addresses = addresses) } }
        rule.waitForIdle()
    }

    @Test fun `an unnamed stop with an address is named after it`() {
        show("a" to Fx.place(city = "Boston", cc = "US", lat = 42.35, lng = -71.06), addresses = mapOf("a" to "2180 Cranston Rd, University Heights"))
        rule.onNodeWithText("2180 CRANSTON RD, UNIVERSITY HEIGHTS").assertIsDisplayed()
        rule.onNodeWithText("2180 Cranston Rd, University Heights").assertIsDisplayed()
        rule.onNodeWithText("UNNAMED STOP").assertIsDisplayed()
        rule.onNodeWithContentDescription("Open 2180 Cranston Rd, University Heights").assertIsDisplayed()
    }

    @Test fun `a named stop keeps its name with the address under it`() {
        show("b" to Fx.place(city = "Boston", cc = "US", name = "Trident Booksellers").copy(address = "338 Newbury St, Boston, MA 02115, USA"))
        rule.onNodeWithText("TRIDENT BOOKSELLERS").assertIsDisplayed()
        rule.onNodeWithText("338 Newbury St, Boston").assertIsDisplayed()
        rule.onNodeWithText("Boston, US").assertDoesNotExist()
    }

    @Test fun `a labelled home keeps its label and shows the street under it`() {
        show("h" to Fx.place(role = Role.HOME, label = "Home", city = "Boston", cc = "US"), addresses = mapOf("h" to "1 Main St, Beacon Hill"))
        // The lab line says Home and so does the title; the street never climbs up to replace it.
        val lines = rule.onNodeWithContentDescription("Open Home").assertIsDisplayed().fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf("HOME", "HOME", "1 Main St, Beacon Hill"), lines.drop(1).take(3))
    }

    @Test fun `without an address the card reads as before and asks for one once on screen`() {
        show(
            "c" to Fx.place(city = "Boston", cc = "US", nVisits = 3),
            "d" to Fx.place(city = "Boston", cc = "US", name = "Trident Booksellers", nVisits = 2).copy(address = "338 Newbury St, Boston, MA 02115, USA", photo = "p"),
            "e" to Fx.place(city = "Boston", cc = "US", name = "Newbury Comics").copy(photo = "p"),
        )
        rule.onNodeWithText("BOSTON").assertIsDisplayed()
        rule.onNodeWithText("NEWBURY COMICS").assertIsDisplayed()
        rule.onAllNodesWithText("Boston, US").assertCountEquals(2)
        // A tag that is named and photographed but has no street still asks, once, for one.
        assertEquals(listOf("c", "e"), seen.sorted())
    }
}
