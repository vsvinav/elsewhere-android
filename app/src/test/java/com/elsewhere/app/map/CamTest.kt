package com.elsewhere.app.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CamTest {
    @Test fun `MapLibre sits one level below Google for the same view`() {
        assertEquals(9f, googleToLibreZoom(10f))
        assertEquals(10f, libreToGoogleZoom(9f))
        assertEquals(-1f, googleToLibreZoom(0f))
    }

    @Test fun `the conversions are inverses`() {
        for (z in listOf(0f, 2.5f, 11.75f, 21f)) assertEquals(z, libreToGoogleZoom(googleToLibreZoom(z)))
    }
}
