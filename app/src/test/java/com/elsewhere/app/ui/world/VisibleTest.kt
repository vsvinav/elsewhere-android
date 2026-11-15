package com.elsewhere.app.ui.world

import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VisibleTest {
    private fun near(r: Rect) = nearViewport(r, 400f, 800f, 200f)

    @Test fun `on screen, or within the margin below or above, counts as visible`() {
        assertTrue(near(Rect(0f, 100f, 400f, 200f)))
        assertTrue(near(Rect(0f, 900f, 400f, 1000f)))   // 100 px below the fold, inside the 200 px margin
        assertTrue(near(Rect(0f, -250f, 400f, -200f)))  // just above, touching the margin
        assertTrue(near(Rect(-50f, 0f, 450f, 50f)))      // wider than the window
    }

    @Test fun `further than the margin on any side is not`() {
        assertFalse(near(Rect(0f, 1001f, 400f, 1100f)))
        assertFalse(near(Rect(0f, -400f, 400f, -201f)))
        assertFalse(near(Rect(601f, 0f, 700f, 50f)))
        assertFalse(near(Rect(-300f, 0f, -201f, 50f)))
    }
}
