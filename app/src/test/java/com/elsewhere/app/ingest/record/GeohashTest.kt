package com.elsewhere.app.ingest.record

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class GeohashTest {
    @Test fun `encodes the canonical examples`() {
        assertEquals("ezs42", geohash(42.6, -5.6, 5))
        assertEquals("u4pruyd", geohash(57.64911, 10.40744, 7))
        assertEquals("u4pruydqqvj", geohash(57.64911, 10.40744, 11))
    }

    @Test fun `defaults to seven characters, enough for one building`() {
        assertEquals(7, geohash(42.3632761, -71.0643721).length)
        assertNotEquals(geohash(42.3632761, -71.0643721), geohash(42.3700, -71.0643721))
    }

    @Test fun `device place ids share a prefix`() {
        assertEquals("dev:ezs42", devicePlaceId(42.6, -5.6, 5))
        assertEquals("dev:u4pruyd", devicePlaceId(57.64911, 10.40744))
    }
}
