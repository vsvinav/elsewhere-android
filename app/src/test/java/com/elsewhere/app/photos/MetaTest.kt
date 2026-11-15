package com.elsewhere.app.photos

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.TimeZone

class MetaTest {
    // Midday UTC: the same calendar day in every zone a test machine is likely to sit in.
    private val now = 1763208000000L // 2025-11-15T12:00:00Z
    private val place = PlaceCtx("lis-chafariz-xyz", 38.713, -9.131, "Lisbon")
    private val fix = Fix(38.7, -9.1, now - 60_000)
    private val exif = Exif(48.86, 2.35, now - 86_400_000)

    @Test fun `the place the camera opened from wins over everything`() {
        assertEquals(PhotoMeta(now, 38.713, -9.131, "lis-chafariz-xyz", "Lisbon"), resolveMeta(place, fix, exif, now))
    }

    @Test fun `a fresh fix beats EXIF`() {
        assertEquals(PhotoMeta(now, 38.7, -9.1), resolveMeta(fix = fix, exif = exif, now = now))
    }

    @Test fun `a fix older than ten minutes is ignored`() {
        val stale = fix.copy(ts = now - FIX_MAX_AGE_MS - 1)
        assertEquals(PhotoMeta(exif.ts!!, 48.86, 2.35), resolveMeta(fix = stale, exif = exif, now = now))
        assertEquals(PhotoMeta(now), resolveMeta(fix = stale, now = now))
        assertEquals(PhotoMeta(now, 38.7, -9.1), resolveMeta(fix = fix.copy(ts = now - FIX_MAX_AGE_MS), now = now))
    }

    @Test fun `EXIF without a timestamp is stamped now`() {
        assertEquals(PhotoMeta(now, 1.0, 2.0), resolveMeta(exif = Exif(1.0, 2.0), now = now))
    }

    @Test fun `nothing known - just the time`() {
        assertEquals(PhotoMeta(now), resolveMeta(now = now))
    }

    @Test fun `stamp lines and barcode`() {
        val tz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            assertEquals(StampLines("LISBON", "15", "NOV 2025"), stampLines(now, "Lisbon"))
            assertEquals("ELSEWHERE", stampLines(now, null).top)
            assertEquals("PHO 20251115 LIS-CH", photoBarcode(now, "lis-chafariz-xyz"))
            assertEquals("PHO 20251115 SOMEWH", photoBarcode(now, null))
        } finally { TimeZone.setDefault(tz) }
    }

    @Test fun `where line - address, else coordinates to 3 dp, else a note`() {
        assertEquals("ALFAMA, LISBOA", whereLine(38.7134, -9.1309, "Alfama, Lisboa"))
        assertEquals("38.713, -9.131", whereLine(38.7134, -9.1309))
        assertEquals("NO LOCATION ATTACHED", whereLine(null, null))
    }
}
