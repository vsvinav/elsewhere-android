package com.elsewhere.app.share

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardRendererTest {
    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    @Test fun `a story renders to a 1080x1920 sheet with the card inked on stock`() {
        val bmp = CardRenderer.render(activity, ShareFormat.STORY) { Box(Modifier.fillMaxWidth().height(100.dp).background(Color.Red)) }
        assertEquals(1080, bmp.width); assertEquals(1920, bmp.height)
        assertEquals(0xFFEFE6D2.toInt(), bmp.getPixel(10, 10))
        // The 400dp card fills 960px at density 2.4; a 100dp box is 240px tall, centred above the footer.
        assertEquals(0xFFFF0000.toInt(), bmp.getPixel(540, (1920 - 120) / 2))
    }

    @Test fun `a free card is as tall as its content plus the pad`() {
        val bmp = CardRenderer.render(activity, ShareFormat.FREE) { Box(Modifier.fillMaxWidth().height(100.dp).background(Color.Red)) }
        assertEquals(1080, bmp.width); assertEquals(234 + 144, bmp.height)
        assertEquals(0xFFFF0000.toInt(), bmp.getPixel(540, 100))
    }
}
