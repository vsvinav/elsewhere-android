package com.elsewhere.app.map

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeatBitmapTest {
    @Test fun `cells under the threshold stay clear and the rest take the palette`() {
        val bmp = heatBitmap(floatArrayOf(0f, HEAT_THRESHOLD / 2, 0.5f, 1f), 4, 1)
        assertEquals(4, bmp.width); assertEquals(1, bmp.height)
        assertEquals(0, Color.alpha(bmp.getPixel(0, 0)))
        assertEquals(0, Color.alpha(bmp.getPixel(1, 0)))
        assertEquals(heatColor(1f), bmp.getPixel(3, 0))
        // Premultiplied storage may shift a translucent channel by a unit.
        val want = heatColor(0.5f); val got = bmp.getPixel(2, 0)
        for (ch in listOf(Color::alpha, Color::red, Color::green, Color::blue)) assertEquals(ch(want).toFloat(), ch(got).toFloat(), 2f)
    }
}
