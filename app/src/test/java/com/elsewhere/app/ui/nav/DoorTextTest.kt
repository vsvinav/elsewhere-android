package com.elsewhere.app.ui.nav

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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

    @Test fun `each figure is a button of its own that opens its door, the plain words are not`() {
        show()
        val buttons = rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        buttons.assertCountEquals(2)
        buttons[0].assertContentDescriptionEquals("Open on the map: 42 days of record")
        buttons[1].assertContentDescriptionEquals("Open on the map: 3 cities")
        rule.onNodeWithContentDescription("Open on the map: 3 cities").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf<Door>(cities), opened)
        rule.onNodeWithText("live from", substring = true).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
    }

    @Test fun `a figure's button sits over the characters it prints`() {
        show()
        val l = layout()
        val want = l.getPathForRange("live from ".length, "live from 42 days".length).getBounds()
        val got = rule.onNodeWithContentDescription("Open on the map: 42 days of record").fetchSemanticsNode().boundsInRoot
        assertEquals(want.left, got.left, 1f); assertEquals(want.right, got.right, 1f); assertEquals(want.top, got.top, 1f); assertEquals(want.bottom, got.bottom, 1f)
    }

    @Test fun `a tap on a figure opens its door, a tap on the words between opens nothing`() {
        show()
        val node = rule.onNodeWithText("live from 42 days of record, 3 cities.")
        val l = layout()
        // Centre of a character inside "42 days", then one inside "live from".
        val inDays = l.getBoundingBox("live from 42".length).center
        val inWords = l.getBoundingBox(2).center
        node.performTouchInput { click(inDays) }
        node.performTouchInput { click(inWords) }
        rule.waitForIdle()
        assertEquals(listOf<Door>(days), opened)
    }

    @Test fun `blank paper past the end of a line opens nothing, even when the line ends in a figure`() {
        val ending = listOf(span("live from "), span("3 cities", cities))
        rule.setContent { ElsewhereTheme { CompositionLocalProvider(LocalOpenDoor provides { opened += it }, LocalMotion provides false) { DoorText(ending, Modifier.fillMaxWidth()) } } }
        val node = rule.onNodeWithText("live from 3 cities")
        val layouts = ArrayList<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val l = layouts.single()
        node.performTouchInput { click(Offset(l.getLineRight(0) + 40f, l.getLineBottom(0) - 1f)) }
        node.performTouchInput { click(Offset(l.getLineRight(0) - 4f, l.getLineBottom(0) - 1f)) }
        rule.waitForIdle()
        assertEquals(listOf<Door>(cities), opened)
    }

    private fun layout(): TextLayoutResult {
        val layouts = ArrayList<TextLayoutResult>()
        rule.onNodeWithText("live from 42 days of record, 3 cities.").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
}
