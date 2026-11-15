package com.elsewhere.app.record

import com.elsewhere.app.Fakes
import com.elsewhere.app.data.Recording
import com.elsewhere.app.data.db.RawFixEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTest {
    private var now = 1_800_000_000L
    private fun fix(ts: Long, lat: Double = 42.37) = RawFixEntity(ts = ts, lat = lat, lng = -71.11, accuracyM = 12f, speedMps = null, altitude = null, activity = null, batteryPct = null)

    @Test fun `ticks every 30 s, looks the city up once per fix, and goes quiet when stopped`() = runTest {
        val control = Fakes.Control(Recording(true, now - 3600, 0L)).apply { fix = fix(now - 180) }
        var lookups = 0
        val live = Live(control, { _, _ -> lookups++; "Cambridge" }, { now }, { ZoneOffset.UTC }, tickMs = 30_000)
        val seen = ArrayList<NowStamp?>()
        val job = live.stamps.onEach { seen += it }.launchIn(backgroundScope)
        runCurrent()
        assertEquals(listOf(NowStamp(42.37, -71.11, "07:57", "Cambridge", "3 min ago")), seen)
        assertEquals(1, control.reads)

        now += 60; advanceTimeBy(30_001)
        assertEquals("4 min ago", seen.last()!!.age)
        assertEquals(2, control.reads)
        assertEquals(1, lookups)

        control.fix = fix(now, lat = 51.5)
        advanceTimeBy(30_000)
        assertEquals(51.5, seen.last()!!.lat)
        assertEquals(2, lookups)

        control.recording.value = Recording()
        runCurrent()
        assertNull(seen.last())
        val reads = control.reads
        advanceTimeBy(120_000)
        assertEquals(reads, control.reads)
        job.cancel()
    }

    @Test fun `paused recording has no stamp`() = runTest {
        val control = Fakes.Control(Recording(true, now - 3600, now + 3600)).apply { fix = fix(now) }
        val live = Live(control, { _, _ -> "Cambridge" }, { now }, { ZoneOffset.UTC }, tickMs = 30_000)
        val seen = ArrayList<NowStamp?>()
        val job = live.stamps.onEach { seen += it }.launchIn(backgroundScope)
        runCurrent()
        assertEquals(listOf<NowStamp?>(null), seen)
        job.cancel()
    }
}
