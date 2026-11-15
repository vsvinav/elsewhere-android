package com.elsewhere.app.ui.tabs.map

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.map.PinLayers
import com.elsewhere.app.map.PinOpts
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class MapHeatToggleTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `the heat chip flips its state, shows the legend while on, and reports each change`() {
        val calls = ArrayList<Boolean>()
        rule.setContent {
            var opts by androidx.compose.runtime.remember { mutableStateOf(PinOpts()) }
            val acts = MapActions(heat = { calls += it; opts = opts.copy(heat = it) })
            ElsewhereTheme { MapScreen(PinLayers(SampleRecord.record), opts, acts) { m -> Box(m) } }
        }
        rule.onNodeWithText("HEAT").assertIsDisplayed().assertIsOff()
        rule.onNodeWithText("COOL").assertDoesNotExist()
        rule.onNodeWithText("HEAT").performClick()
        assertEquals(listOf(true), calls)
        rule.onNodeWithText("HEAT").assertIsOn()
        rule.onNodeWithText("COOL").assertIsDisplayed()
        rule.onNodeWithText("WARM").assertIsDisplayed()
        rule.onNodeWithContentDescription("Cool to warm: warmest where you go most").assertIsDisplayed()
        rule.onNodeWithText("HEAT").performClick()
        assertEquals(listOf(true, false), calls)
        rule.onNodeWithText("HEAT").assertIsOff()
        rule.onNodeWithText("COOL").assertDoesNotExist()
        rule.onNodeWithText("WARM").assertDoesNotExist()
    }
}
