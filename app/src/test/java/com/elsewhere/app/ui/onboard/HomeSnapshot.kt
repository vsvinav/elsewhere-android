package com.elsewhere.app.ui.onboard

import androidx.compose.runtime.CompositionLocalProvider
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.data.Units
import com.elsewhere.app.ingest.Progress
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/** The "where is home" step: one card named through Places, one still carrying its Nominatim street while Places is asked, the stamp turning. */
class HomeSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    private object NoOp : OnboardActions {
        override fun setName(s: String) {}
        override fun pickHome(c: Candidate) {}
        override fun addHome(c: Candidate) {}
        override fun setHomeFrom(i: Int, m: YearMonth) {}
        override fun setHomeTo(i: Int, m: YearMonth?) {}
        override fun removeHome(i: Int) {}
        override fun toggleWork(c: Candidate) {}
        override fun setWorkFrom(i: Int, m: YearMonth) {}
        override fun setWorkTo(i: Int, m: YearMonth?) {}
        override fun removeWork(i: Int) {}
        override fun setUnits(u: Units) {}
        override fun next() {}
        override fun back() {}
        override fun skip() {}
        override fun finish() {}
    }

    @Test fun naming() = paparazzi.snapshot {
        val state = OnboardState(
            step = Step.Home,
            sleep = listOf(
                Candidate("b", 42.36, -71.06, "Boston", "US", "Barton Hills Bungalow", null, 212.0, 0.82, listOf("b", "b2", "b3")),
                Candidate("a", 30.27, -97.74, "Austin", "US", "Austin", "Mary St, Bouldin Creek", 31.0, 0.35, listOf("a"), named = false),
            ),
            naming = Progress(3, 8), asking = true,
        )
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { QuestionsScreen(state, NoOp, onCancel = null) } }
    }
}
