package com.elsewhere.app.sonar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SonarToneTest {
    private val s = SonarTone.render()

    @Test fun `exactly 1,2 s at 48 kHz`() {
        assertEquals(57_600, s.size)
        assertEquals(57_600, SonarTone.pcm16().size)
    }

    @Test fun `peak sits at or under minus six dBFS and well above silence`() {
        val peak = SonarTone.peakDbfs(s)
        assertTrue(peak <= -6.0 && peak > -6.1, "peak $peak")
    }

    @Test fun `the first tone attacks within four ms and the echo repeats at 180 ms`() {
        val first = s.indexOfFirst { abs(it) > 0.01 }
        assertTrue(first in 0..200, "first audible sample $first")
        val around = (0.18 * 48_000).toInt()
        val echoBand = (around until around + 2000).maxOf { abs(s[it]) }
        assertTrue(echoBand > 0.02, "echo $echoBand")
    }

    @Test fun `silent by 1,2 s and already fading past 1,1 s`() {
        assertTrue(abs(s.last()) < 1e-4, "last ${s.last()}")
        val tail = (57_000 until 57_600).maxOf { abs(it.let { i -> s[i] }) }
        assertTrue(tail < 0.01, "tail $tail")
        val late = (52_800 until 57_599).maxOf { abs(s[it]) }
        val mid = (10_000 until 20_000).maxOf { abs(s[it]) }
        assertTrue(late < mid, "fade late=$late mid=$mid")
    }

    @Test fun `the wav header matches the payload`() {
        val w = SonarTone.wav()
        assertEquals(44 + 57_600 * 2, w.size)
        assertEquals("RIFF", String(w, 0, 4))
        assertEquals("WAVE", String(w, 8, 4))
        assertEquals("data", String(w, 36, 4))
    }
}
