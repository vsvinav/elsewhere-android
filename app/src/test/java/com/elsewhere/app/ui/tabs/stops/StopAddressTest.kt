package com.elsewhere.app.ui.tabs.stops

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StopAddressTest {
    @Test fun `a Places address is cut to its first line and the city`() {
        assertEquals("1 Devonshire Pl, Boston", stopAddress("1 Devonshire Pl, Boston, MA 02109, USA", "Boston", "9 Other St, Downtown"))
    }

    @Test fun `without a Places address the Nominatim line stands in`() {
        assertEquals("2180 Cranston Rd, University Heights", stopAddress(null, "Boston", "2180 Cranston Rd, University Heights"))
    }

    @Test fun `with neither there is nothing to show`() {
        assertNull(stopAddress(null, "Boston", null))
        assertNull(stopAddress("", "Boston", null))
    }

    @Test fun `a first line that is just the city counts as missing`() {
        assertEquals("Somewhere Rd, Allston", stopAddress("Boston, MA, USA", "Boston", "Somewhere Rd, Allston"))
        assertNull(stopAddress("Boston, MA, USA", "Boston", null))
    }
}
