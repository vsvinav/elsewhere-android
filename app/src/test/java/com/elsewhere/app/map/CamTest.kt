package com.elsewhere.app.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CamTest {
    @Test fun `MapLibre sits one level below Google for the same view`() {
        assertEquals(9f, googleToLibreZoom(10f))
        assertEquals(10f, libreToGoogleZoom(9f))
        assertEquals(-1f, googleToLibreZoom(0f))
    }

    @Test fun `a camera is near another within the SDKs' own rounding, so a redundant write can be skipped`() {
        val c = Cam(48.8566, 2.3522, 12.5f)
        assertTrue(c.near(c))
        assertTrue(c.near(Cam(48.8566 + 1e-9, 2.3522 - 1e-9, 12.5f + 1e-5f)))
        assertFalse(c.near(c.copy(lat = c.lat + 1e-5)))
        assertFalse(c.near(c.copy(zoom = 12.51f)))
        assertFalse(c.near(c.copy(bearing = 1f)))
    }

    @Test fun `the conversions are inverses`() {
        for (z in listOf(0f, 2.5f, 11.75f, 21f)) assertEquals(z, libreToGoogleZoom(googleToLibreZoom(z)))
    }
}
