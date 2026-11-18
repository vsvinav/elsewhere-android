package com.elsewhere.app.record

import com.elsewhere.app.data.Recording
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test fun `a start without an action asks the settings, so a running pause holds and an enabled record runs`() {
        val paused = Recording(enabled = true, since = now - 3600, pausedUntil = now + 3600)
        assertTrue(pausedFor(null, { paused }, now))
        assertFalse(pausedFor(null, { paused.copy(pausedUntil = now - 1) }, now))
        assertFalse(pausedFor(null, { Recording(enabled = true, since = now - 86400) }, now))
    }

    @Test fun `an explicit action decides on its own without reading the settings`() {
        val unread: () -> Recording = { error("settings must not be read") }
        assertTrue(pausedFor(RecordService.ACTION_PAUSE, unread, now))
        assertFalse(pausedFor("", unread, now))
    }

    @Test fun `the notification line`() {
        assertEquals("Elsewhere is recording · last fix 2 min ago", notificationText(now - 120, now))
    }
}
