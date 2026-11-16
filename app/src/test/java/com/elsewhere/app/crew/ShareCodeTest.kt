package com.elsewhere.app.crew

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class ShareCodeTest {
    @Test fun `a fresh code is PT, three, four, from the Crockford alphabet`() {
        val code = ShareCode.create(Random(7), createdAt = 1_000L)
        assertTrue(Regex("PT-[0-9A-HJKMNP-TV-Z]{3}-[0-9A-HJKMNP-TV-Z]{4}").matches(code.code), code.code)
        assertEquals(1_000L, code.createdAt)
        assertEquals(3, code.bucket.length)
        assertEquals(4, code.secret.length)
        assertEquals(code.code, "PT-${code.bucket}-${code.secret}")
    }

    @Test fun `codes never carry I L O or U`() {
        val r = Random(1)
        repeat(500) { val c = ShareCode.create(r, 0L).code; assertFalse(c.any { it in "ILOU" }, c) }
    }

    @Test fun `parsing forgives case, spaces, missing dashes and the prefix`() {
        assertEquals("PT-MRT-7Q4K", ShareCode.parse("pt-mrt-7q4k"))
        assertEquals("PT-MRT-7Q4K", ShareCode.parse(" MRT 7Q4K "))
        assertEquals("PT-MRT-7Q4K", ShareCode.parse("MRT7Q4K"))
        assertEquals("PT-MRT-7Q4K", ShareCode.parse("PT-MRT-7Q4K"))
    }

    @Test fun `parsing maps the lookalikes Crockford allows`() {
        assertEquals("PT-A1B-0C11", ShareCode.parse("PT-AIB-OCLl"))
    }

    @Test fun `anything else is not a code`() {
        assertNull(ShareCode.parse(""))
        assertNull(ShareCode.parse("PT-MRT-7Q4"))
        assertNull(ShareCode.parse("PT-MRT-7Q4KX"))
        assertNull(ShareCode.parse("PT-M*T-7Q4K"))
        assertNull(ShareCode.parse("PT-MUT-7Q4K"))
    }

    @Test fun `a code lives a day`() {
        val c = ShareCode("PT-MRT-7Q4K", createdAt = 0L)
        assertFalse(c.expired(ShareCode.TTL_MS - 1))
        assertTrue(c.expired(ShareCode.TTL_MS))
        assertEquals("24 h", c.remaining(0L))
        assertEquals("23 h", c.remaining(3_600_000L))
        assertEquals("1 h", c.remaining(ShareCode.TTL_MS - 3_600_000L))
        assertEquals("42 min", c.remaining(ShareCode.TTL_MS - 42 * 60_000L))
        assertEquals("expired", c.remaining(ShareCode.TTL_MS))
    }

    @Test fun `the deep link carries the code and parses back`() {
        assertEquals("elsewhere://pair/PT-MRT-7Q4K", ShareCode.link("PT-MRT-7Q4K"))
        assertEquals("PT-MRT-7Q4K", ShareCode.fromLink("elsewhere://pair/PT-MRT-7Q4K"))
        assertEquals("PT-MRT-7Q4K", ShareCode.fromLink("elsewhere://pair/pt-mrt-7q4k"))
        assertNull(ShareCode.fromLink("https://example.com/pair/PT-MRT-7Q4K"))
        assertNull(ShareCode.fromLink("elsewhere://map"))
        assertNull(ShareCode.fromLink(null))
    }
}
