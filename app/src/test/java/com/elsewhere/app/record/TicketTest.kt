package com.elsewhere.app.record

import com.elsewhere.app.data.Recording
import com.elsewhere.app.data.db.RawFixEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

class TicketTest {
    private val now = 1_800_000_000L
    private fun fix(ts: Long) = RawFixEntity(ts = ts, lat = 42.37, lng = -71.11, accuracyM = 12f, speedMps = null, altitude = null, activity = null, batteryPct = null)

    @Test fun `age buckets and their boundaries`() {
        assertEquals("just now", age(now, now))
        assertEquals("just now", age(now - 59, now))
        assertEquals("1 min ago", age(now - 60, now))
        assertEquals("3 min ago", age(now - 180, now))
        assertEquals("59 min ago", age(now - 3599, now))
        assertEquals("1 h ago", age(now - 3600, now))
        assertEquals("23 h ago", age(now - 86399, now))
        assertEquals("1 d ago", age(now - 86400, now))
        assertEquals("2 d ago", age(now - 2 * 86400, now))
        assertEquals("just now", age(now + 30, now))
    }

    @Test fun `each state prints its headline and tone`() {
        val stats = RecordStats(1204, 7, now - 180)
        assertEquals(Ticket("RECORDING", "1,204 fixes", "last 3 min ago", TicketTone.RED), ticket(RecordState.RECORDING, stats, now))
        assertEquals(Ticket("PAUSED", "1,204 fixes", "last 3 min ago", TicketTone.INK), ticket(RecordState.PAUSED, stats, now))
        assertEquals(Ticket("OFF", "1,204 fixes", "last 3 min ago", TicketTone.GHOST), ticket(RecordState.OFF, stats, now))
        assertEquals(Ticket("NEEDS PERMISSION", "1,204 fixes", "last 3 min ago", TicketTone.INK), ticket(RecordState.NEEDS_PERMISSION, stats, now))
    }

    @Test fun `no stats or no fix yet`() {
        assertEquals(Ticket("OFF", "0 fixes", "no fixes yet", TicketTone.GHOST), ticket(RecordState.OFF, null, now))
        assertEquals(Ticket("RECORDING", "1 fix", "no fixes yet", TicketTone.RED), ticket(RecordState.RECORDING, RecordStats(1, 0, null), now))
    }

    @Test fun `the state follows the settings and the location permission`() {
        assertEquals(RecordState.NEEDS_PERMISSION, recordState(Recording(true, now, 0L), fine = false, now = now))
        assertEquals(RecordState.OFF, recordState(Recording(), fine = true, now = now))
        assertEquals(RecordState.PAUSED, recordState(Recording(true, now, now + 100), fine = true, now = now))
        assertEquals(RecordState.RECORDING, recordState(Recording(true, now, now), fine = true, now = now))
    }

    @Test fun `the now stamp prints the fix's time, city and age`() {
        val s = nowStamp(fix(now - 180), now, { _, _ -> "Cambridge" }, zone = ZoneOffset.UTC)!!
        assertEquals(42.37, s.lat); assertEquals(-71.11, s.lng)
        assertEquals("07:57", s.time)
        assertEquals("Cambridge", s.city)
        assertEquals("3 min ago", s.age)
    }

    @Test fun `without a city within reach the stamp says elsewhere`() {
        assertEquals("ELSEWHERE", nowStamp(fix(now), now, { _, _ -> null }, zone = ZoneOffset.UTC)!!.city)
    }

    @Test fun `no stamp without a fix, with a stale one, or when not recording`() {
        assertNull(nowStamp(null, now, { _, _ -> "Cambridge" }))
        assertNull(nowStamp(fix(now - 30 * 60 - 1), now, { _, _ -> "Cambridge" }))
        assertEquals("30 min ago", nowStamp(fix(now - 30 * 60), now, { _, _ -> "Cambridge" })!!.age)
        assertNull(nowStamp(fix(now), now, { _, _ -> "Cambridge" }, recording = false))
    }
}
