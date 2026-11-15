package com.elsewhere.app.ui.nav

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class DoorClickTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `tapping a figure opens its door`() {
        val opened = ArrayList<Door>()
        val door = Door.Range(0L, 86_399L, "42 countries")
        rule.setContent {
            ElsewhereTheme {
                CompositionLocalProvider(LocalOpenDoor provides { opened += it }) {
                    Door(door) { Text("42 countries") }
                    Door(null) { Text("no door") }
                }
            }
        }
        rule.onNodeWithContentDescription("Open on the map: 42 countries").assertHasClickAction().performClick()
        rule.waitForIdle()
        assertEquals(listOf<Door>(door), opened)
        rule.onNodeWithText("no door").assertHasNoClickAction()
    }
}
