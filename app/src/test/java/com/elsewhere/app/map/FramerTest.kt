package com.elsewhere.app.map

import androidx.compose.ui.unit.IntSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FramerTest {
    private val moves = ArrayList<Pair<Cam, Int>>()
    private val framer = Framer { cam, ms -> moves += cam to ms }
    private val paris = Bounds(48.8, 2.3, 48.9, 2.4)

    @Test fun `a fit asked before the view has a size runs once the size arrives, animated`() {
        framer.fit(paris, 40)
        assertEquals(emptyList<Pair<Cam, Int>>(), moves)
        framer.size = IntSize(1080, 1920)
        assertEquals(listOf(camForBounds(paris, 1080, 1920, 40) to Framer.ANIMATE_MS), moves)
        framer.size = IntSize(1920, 1080) // a rotation later does not re-frame on its own
        assertEquals(1, moves.size)
    }

    @Test fun `only the latest fit asked before layout is kept`() {
        framer.fit(Bounds(0.0, 0.0, 1.0, 1.0), 0)
        framer.fit(paris, 0)
        framer.size = IntSize(800, 600)
        assertEquals(listOf(camForBounds(paris, 800, 600, 0) to Framer.ANIMATE_MS), moves)
    }

    @Test fun `with a size known, every fit moves the camera right away`() {
        framer.size = IntSize(800, 600)
        framer.fit(paris, 10); framer.fit(paris, 10)
        assertEquals(2, moves.size)
        assertEquals(camForBounds(paris, 800, 600, 10), moves[0].first)
    }

    @Test fun `the frame is solved in dp, so a sharper screen does not zoom in tighter`() {
        val sharp = Framer { cam, _ -> moves += cam to 0 }.apply { density = 2f; size = IntSize(1024, 1024) }
        sharp.fit(paris, 80)
        assertEquals(camForBounds(paris, 512, 512, 40), moves.single().first)
    }

    @Test fun `a host that frames bounds itself gets them in physical px, once the view has a size`() {
        val asked = ArrayList<Triple<Bounds, IntSize, Int>>()
        val own = Framer { b, size, pad -> asked += Triple(b, size, pad) }.apply { density = 2f }
        own.fit(paris, 80)
        assertEquals(emptyList<Triple<Bounds, IntSize, Int>>(), asked)
        own.size = IntSize(1080, 1920)
        assertEquals(listOf(Triple(paris, IntSize(1080, 1920), 80)), asked)
    }
}
