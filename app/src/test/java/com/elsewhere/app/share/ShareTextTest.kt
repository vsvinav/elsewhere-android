package com.elsewhere.app.share

import com.elsewhere.app.analytics.Night
import com.elsewhere.app.analytics.NightKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Twin of web/src/share/text.test.ts. */
class ShareTextTest {
    private fun n(date: String, kind: NightKind, city: String? = null) = Night(date, kind, city)

    @Test fun `short ranges wrap by week, Monday first, with the caption and site`() {
        // 2025-01-01 is a Wednesday: two blank cells of padding.
        val t = bedText(listOf(n("2025-01-01", NightKind.HOME), n("2025-01-02", NightKind.NEW, "Paris"), n("2025-01-03", NightKind.REPEAT, "Paris"), n("2025-01-04", NightKind.NONE)), "ELSEWHERE 2025")
        assertEquals(listOf("ELSEWHERE 2025 · 2 nights away · 1 city", "　　⬛🟥🟨⬜", SITE), t.split("\n"))
    }

    @Test fun `long ranges get one row per month`() {
        val nights = ArrayList<Night>()
        var d = LocalDate.of(2025, 1, 1)
        while (d < LocalDate.of(2025, 4, 1)) { nights.add(n(d.toString(), NightKind.HOME)); d = d.plusDays(1) }
        val rows = bedText(nights, "X").split("\n")
        assertEquals(5, rows.size)
        assertEquals(31, rows[1].codePointCount(0, rows[1].length))
        assertEquals(28, rows[2].codePointCount(0, rows[2].length))
    }

    @Test fun `an empty strip is just the title and site`() {
        assertEquals("X\n$SITE", bedText(emptyList(), "X"))
    }
}
