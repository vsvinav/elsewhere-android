package com.elsewhere.app.analytics

import com.elsewhere.app.Fx.iso
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TourTest {
    @Test fun `nightsIn counts local 03_00 crossings`() {
        // Boston (UTC-5): 20:00 → 09:00 next day spans one 03:00
        assertEquals(1, nightsIn(iso("2025-01-10T01:00:00Z"), iso("2025-01-10T14:00:00Z"), -300))
        // a lunch stop sleeps nowhere
        assertEquals(0, nightsIn(iso("2025-01-10T17:00:00Z"), iso("2025-01-10T19:00:00Z"), -300))
        // three nights in Indore (UTC+5:30)
        assertEquals(3, nightsIn(iso("2025-12-20T12:00:00Z"), iso("2025-12-23T12:00:00Z"), 330))
    }
}
