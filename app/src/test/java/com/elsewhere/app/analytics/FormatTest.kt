package com.elsewhere.app.analytics

import com.elsewhere.app.Fx.iso
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Pins the strings the web's Intl formatting produces in en-US. */
class FormatTest {
    @Test fun `fmtKm rounds under a thousand and abbreviates above`() {
        assertEquals("512 km", fmtKm(512.4))
        assertEquals("1.2k km", fmtKm(1234.0))
        assertEquals("3 km", fmtKm(2.5))
    }

    @Test fun `toFixed rounds the exact binary value like JS`() {
        assertEquals("0.1", toFixed(0.15, 1))
        assertEquals("1.3", toFixed(1.25, 1))
        assertEquals("1.1", toFixed(1.15, 1))
    }

    @Test fun `fmtDate and fmtTime match en-US output, shifted by tz minutes`() {
        val t = iso("2025-01-01T09:05:00Z")
        assertEquals("Wed, Jan 1, 2025", fmtDate(t))
        assertEquals("9:05 AM", fmtTime(t))
        assertEquals("Tue, Dec 31, 2024", fmtDate(t, -600))
        assertEquals("11:05 PM", fmtTime(t, -600))
    }
}
