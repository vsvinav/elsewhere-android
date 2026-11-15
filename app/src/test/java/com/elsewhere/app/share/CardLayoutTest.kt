package com.elsewhere.app.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Twin of the maths in web/src/share/saveCard.ts: the file name and where the card lands on the sheet. */
class CardLayoutTest {
    @Test fun `file names slug the title and suffix the format`() {
        assertEquals("elsewhere-vinav-s-elsewhere.png", cardFileName("Vinav's Elsewhere", ShareFormat.FREE))
        assertEquals("elsewhere-nights-story.png", cardFileName("  Nights ", ShareFormat.STORY))
        assertEquals("elsewhere-routes-post.png", cardFileName("ROUTES", ShareFormat.POST))
        assertEquals("elsewhere-card.png", cardFileName("…", ShareFormat.FREE))
        assertEquals(48, cardFileName("x".repeat(80), ShareFormat.FREE).removePrefix("elsewhere-").removeSuffix(".png").length)
    }

    @Test fun `a free card prints at full width inside a 72px pad`() {
        val p = cardPlacement(ShareFormat.FREE, cardHeightDp = 1000f)
        assertEquals(1080, p.sheetWidth)
        assertEquals(2340 + 144, p.sheetHeight)
        assertEquals(72, p.x); assertEquals(72, p.y)
        assertEquals(936, p.cardWidth); assertEquals(2340, p.cardHeight)
    }

    @Test fun `a free card never runs past four times its width, shrinking and centring instead`() {
        val p = cardPlacement(ShareFormat.FREE, cardHeightDp = 4000f)
        assertEquals(1080, p.sheetWidth); assertEquals(4320, p.sheetHeight)
        assertEquals(4320 - 144, p.cardHeight)
        assertEquals(Math.round(4176 / 4000f * 400), p.cardWidth)
        assertEquals((1080 - p.cardWidth) / 2, p.x); assertEquals(72, p.y)
    }

    @Test fun `a short card on a story fills the width and sits centred above the footer`() {
        val p = cardPlacement(ShareFormat.STORY, cardHeightDp = 400f)
        assertEquals(1080, p.sheetWidth); assertEquals(1920, p.sheetHeight)
        assertEquals(960, p.cardWidth); assertEquals(960, p.cardHeight)
        assertEquals(60, p.x); assertEquals((1920 - 120 - 960) / 2, p.y)
    }

    @Test fun `a tall card on a post shrinks to the room above the footer`() {
        val p = cardPlacement(ShareFormat.POST, cardHeightDp = 1000f)
        assertEquals(1350 - 120 - 120, p.cardHeight)
        assertEquals((1110 * 0.4f).toInt(), p.cardWidth)
        assertEquals((1080 - p.cardWidth) / 2, p.x); assertEquals(60, p.y)
    }
}
