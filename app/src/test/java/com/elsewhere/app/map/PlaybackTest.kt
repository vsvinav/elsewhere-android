package com.elsewhere.app.map

import com.elsewhere.app.Fx
import com.elsewhere.app.Fx.H
import com.elsewhere.app.Fx.act
import com.elsewhere.app.Fx.visit
import com.elsewhere.app.analytics.Lens
import com.elsewhere.app.data.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackTest {
    private val a = LatLng(42.0, -71.0); private val b = LatLng(37.0, -122.0)
    private val data = Fx.record(
        visits = listOf(visit(0, 2 * H, lng = a.lng, lat = a.lat), visit(3 * H, 10 * H, lng = a.lng, lat = a.lat), visit(16 * H, 20 * H, lng = b.lng, lat = b.lat)),
        activities = listOf(
            act(2 * H, 3 * H, "WALKING", 5_000.0, a.lng, a.lat, a.lng, a.lat),
            act(10 * H, 16 * H, "FLYING", 4_300_000.0, a.lng, a.lat, b.lng, b.lat),
        ),
        range = 0L..20 * H,
    )

    @Test fun `a stay plays out in its screen seconds and the shown rate eases toward the pace`() {
        var s = PlayState(playhead = 0.0, playing = true, speed = 1.0, rate = 3600.0)
        // The first stay lasts two hours: 1.0 s on screen at pace 1 → 7200 s/s.
        s = tickPlayback(data, s, 1.0 / 60)
        assertTrue(s.playhead > 100 && s.playhead < 130)
        assertTrue(s.rate > 3600 && s.rate < 7200)
        repeat(29) { s = tickPlayback(data, s, 1.0 / 60) }
        assertTrue(s.rate > 5000 && s.rate < 7200)
    }

    @Test fun `the playhead stops at the end of the record`() {
        var s = PlayState(playhead = 20.0 * H - 1, playing = true, speed = 1.0, rate = 3600.0)
        repeat(5) { s = tickPlayback(data, s, 0.1) }
        assertFalse(s.playing)
        assertEquals(20.0 * H, s.playhead, 1e-9)
    }

    @Test fun `under a lens the playhead cuts to the next matching leg and stops after the last`() {
        var s = PlayState(playhead = 1.0 * H, playing = true, speed = 1.0, rate = 3600.0, lens = Lens.FLY)
        s = tickPlayback(data, s, 1.0 / 60)
        assertTrue(s.playhead >= 10 * H && s.playhead < 11 * H)
        s = tickPlayback(data, s.copy(playhead = 16.0 * H + 5), 1.0 / 60)
        assertFalse(s.playing)
    }

    @Test fun `a lens leg plays through at its own pace`() {
        var s = PlayState(playhead = 10.0 * H + 1, playing = true, speed = 1.0, rate = 3600.0, lens = Lens.FLY)
        s = tickPlayback(data, s, 1.0 / 60)
        // 6 h flight over 7 s of screen time ≈ 3086 s/s.
        assertEquals(10 * H + 1 + 3086.0 / 60, s.playhead, 2.0)
    }
}

class ChromeGateTest {
    @Test fun `the chrome sees the clock at most four times a second but every other change at once`() {
        val g = ChromeGate(250)
        val s0 = PlayState(playhead = 0.0, playing = true)
        assertTrue(g.pass(s0, 1000))
        assertFalse(g.pass(s0.copy(playhead = 16.0, rate = 3700.0), 1016))
        assertFalse(g.pass(s0.copy(playhead = 32.0), 1032))
        assertTrue(g.pass(s0.copy(playhead = 250.0), 1250))
        // A pause, a new speed or a lens is never held back, and it resets the clock's beat.
        assertTrue(g.pass(s0.copy(playhead = 260.0, playing = false), 1260))
        assertFalse(g.pass(s0.copy(playhead = 270.0, playing = false), 1270))
        assertTrue(g.pass(s0.copy(playhead = 280.0, playing = false, speed = 2.0), 1280))
        assertTrue(g.pass(s0.copy(playhead = 290.0, playing = false, speed = 2.0, lens = Lens.FLY), 1290))
        assertTrue(g.pass(s0.copy(playhead = 300.0, playing = false, speed = 2.0, lens = Lens.FLY, follow = false), 1300))
        assertFalse(g.pass(s0.copy(playhead = 310.0, playing = false, speed = 2.0, lens = Lens.FLY, follow = false), 1310))
    }
}
