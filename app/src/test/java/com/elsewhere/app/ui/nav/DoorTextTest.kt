package com.elsewhere.app.ui.nav

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
// Real glyph metrics, so a tap can be aimed at one figure in the paragraph.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DoorTextTest {
    @get:Rule val rule = createComposeRule()

    private val days = Door.Range(0L, 86_399L, "42 days of record")
    private val cities = Door.Places(listOf("a", "b"), "3 cities")
    private val spans = listOf(span("live from "), span("42 days", days), span(" of record, "), span("3 cities", cities), span("."))
    private val opened = ArrayList<Door>()

    private fun show() {
        rule.setContent { ElsewhereTheme { CompositionLocalProvider(LocalOpenDoor provides { opened += it }, LocalMotion provides false) { DoorText(spans) } } }
    }

    @Test fun `each figure is an accessibility action that opens its door, the plain words are not`() {
        show()
        val node = rule.onNodeWithText("live from 42 days of record, 3 cities.").fetchSemanticsNode()
        val actions = node.config[SemanticsActions.CustomActions]
        assertEquals(listOf("Open on the map: 42 days of record", "Open on the map: 3 cities"), actions.map { it.label })
        actions[1].action()
        assertEquals(listOf<Door>(cities), opened)
    }

    @Test fun `a tap on a figure opens its door, a tap on the words between opens nothing`() {
        show()
        val node = rule.onNodeWithText("live from 42 days of record, 3 cities.")
        val layouts = ArrayList<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val l = layouts.single()
        // Centre of a character inside "42 days", then one inside "live from".
        val inDays = l.getBoundingBox("live from 42".length).center
        val inWords = l.getBoundingBox(2).center
        node.performTouchInput { click(inDays) }
        node.performTouchInput { click(inWords) }
        rule.waitForIdle()
        assertEquals(listOf<Door>(days), opened)
    }
}
