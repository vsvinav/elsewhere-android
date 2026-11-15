package com.elsewhere.app.ingest

import com.elsewhere.app.data.Settings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EnrichJobTest {
    @Test fun `a run needs a record and a key Places accepted`() {
        val ok = Settings(mapsKey = "k", keyPlaces = true, keyTiles = false)
        assertEquals(EnrichJob("sha", "k"), enrichJob("sha", ok))
        assertNull(enrichJob(null, ok))
        assertNull(enrichJob("sha", ok.copy(mapsKey = "")))
        assertNull(enrichJob("sha", ok.copy(keyPlaces = false)))
    }
}
