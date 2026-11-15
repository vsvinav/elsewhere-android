package com.elsewhere.app.ui.shell

import com.elsewhere.app.Fx
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** Same cases as web/src/app/yearWheel.test.ts. */
class YearWheelTest {
    // A record from 2019-03-04 to 2025-11-15, the shared fixture for both platforms.
    private val record = Fx.utc(2019, 2, 4)..(Fx.utc(2025, 10, 15) + 86399)
    private val y2022 = Fx.utc(2022, 0, 1)..(Fx.utc(2023, 0, 1) - 1)

    @Test fun `lists every calendar year the record touches`() {
        assertEquals(listOf(2019, 2020, 2021, 2022, 2023, 2024, 2025), yearsOf(record))
        assertEquals(listOf(2024), yearsOf(Fx.utc(2024, 4, 1)..Fx.utc(2024, 4, 2)))
    }

    @Test fun `cuts a year span to the record at the ends`() {
        assertEquals(y2022, yearSpan(record, 2022))
        assertEquals(record.first..(Fx.utc(2020, 0, 1) - 1), yearSpan(record, 2019))
        assertEquals(Fx.utc(2025, 0, 1)..record.last, yearSpan(record, 2025))
    }

    @Test fun `names the one year a selection covers, else null`() {
        assertEquals(WheelState(2022, false, y2022.first, y2022.last), wheelState(record, y2022))
        assertEquals(WheelState(null, true, record.first, record.last), wheelState(record, record))
        // A hand-picked window is neither a year nor the whole record.
        assertEquals(WheelState(null, false, Fx.utc(2022, 0, 1), Fx.utc(2022, 5, 1)), wheelState(record, Fx.utc(2022, 0, 1)..Fx.utc(2022, 5, 1)))
        assertFalse(wheelState(record, (record.last - 90 * 86400)..record.last).all)
        // The first and last years count whether or not the chip cut them to the record.
        assertEquals(2019, wheelState(record, Fx.utc(2019, 0, 1)..(Fx.utc(2020, 0, 1) - 1)).year)
        assertEquals(2025, wheelState(record, yearSpan(record, 2025)).year)
    }

    @Test fun `steps a whole year at a time and stops at the record`() {
        assertEquals(yearSpan(record, 2023), stepYear(record, y2022, 1))
        assertEquals(yearSpan(record, 2021), stepYear(record, y2022, -1))
        assertEquals(y2022, stepYear(record, y2022, 0))
        assertEquals(yearSpan(record, 2025), stepYear(record, yearSpan(record, 2025), 1))
        assertEquals(yearSpan(record, 2019), stepYear(record, yearSpan(record, 2019), -1))
    }

    @Test fun `from all, forward goes to the first year and back to the last`() {
        assertEquals(yearSpan(record, 2019), stepYear(record, record, 1))
        assertEquals(yearSpan(record, 2025), stepYear(record, record, -1))
        assertEquals(record, stepYear(record, record, 0))
    }

    @Test fun `turns drag distance into a year, rounded and clamped`() {
        assertEquals(2024, dragYear(record, 2022, 250f))
        assertEquals(2020, dragYear(record, 2022, -250f))
        assertEquals(2022, dragYear(record, 2022, 59f))
        assertEquals(2023, dragYear(record, 2022, 60f))
        assertEquals(2019, dragYear(record, 2022, -1000f))
        assertEquals(2025, dragYear(record, 2022, 1000f))
        assertEquals(2024, dragYear(record, null, -120f))
        assertEquals(2024, dragYear(record, 2022, 500f, 250f))
    }
}
