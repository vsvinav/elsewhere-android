package com.elsewhere.app.record

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecordTextTest {
    private val now = 1_800_000_000L

    @Test fun `last fix reads as a relative time`() {
        assertEquals("no fix yet", lastFixText(null, now))
        assertEquals("last fix just now", lastFixText(now - 20, now))
        assertEquals("last fix 2 min ago", lastFixText(now - 150, now))
        assertEquals("last fix 3 h ago", lastFixText(now - 3 * 3600 - 100, now))
        assertEquals("last fix 2 days ago", lastFixText(now - 2 * 86400 - 5, now))
    }

    @Test fun `the notification line`() {
        assertEquals("Elsewhere is recording · last fix 2 min ago", notificationText(now - 120, now))
    }
}
