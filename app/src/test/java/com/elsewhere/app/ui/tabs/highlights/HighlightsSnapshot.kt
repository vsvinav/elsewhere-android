package com.elsewhere.app.ui.tabs.highlights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.OnThisDay
import com.elsewhere.app.OnThisDay.at
import com.elsewhere.app.OnThisDay.noHome
import com.elsewhere.app.analytics.Answer
import com.elsewhere.app.analytics.FollowUpId
import com.elsewhere.app.analytics.followUps
import com.elsewhere.app.analytics.highlightFor
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Poster
import org.junit.Rule
import org.junit.Test

class HighlightsSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")
    private val data = OnThisDay.data()
    private val kyoto = highlightFor(data, "2025-11-14")!!
    private val noop: OpenDoor = { _, _, _ -> }

    private fun snap(content: @Composable () -> Unit) = paparazzi.snapshot {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { Column(Modifier.fillMaxWidth().background(Poster.Stock).padding(12.dp)) { content() } } }
    }

    @Test fun card() = snap { HighlightCard(data, noop, today = "2025-11-14") }
    @Test fun cardThisWeek() = snap { HighlightCard(data, noop, today = "2025-11-20") }
    @Test fun quiet() = snap { HighlightCard(data, noop, today = "2025-07-20") }
    @Test fun short() = snap { HighlightCard(data.copy(meta = data.meta.copy(range = at(2023, 11, 11, 8)..at(2024, 5, 1))), noop, today = "2024-04-10") }
    @Test fun unnamedCity() = snap {
        HighlightCard(data.copy(places = data.places + ("nyc-hotel" to data.places.getValue("nyc-hotel").copy(city = null))), noop, today = "2028-02-29", onKeyStep = {})
    }
    @Test fun sheetMenu() = snap { AskMoreContent(data, kyoto, "2025-11-14", {}, noop) }
    @Test fun sheetThread() = snap {
        val fus = followUps(data, kyoto, today = "2025-11-14").associateBy { it.id }
        val chain = listOf(FollowUpId.STAY, FollowUpId.DISTANCE).map { Step(fus.getValue(it), kyoto) }
        AskMoreContent(data, kyoto, "2025-11-14", {}, noop, initial = chain)
    }
    @Test fun answerTrip() = snap { AnswerPass(data, Step(followUps(data, kyoto, today = "2025-11-14").first { it.id == FollowUpId.TRIP }, kyoto), noop) }
    @Test fun answerRoute() = snap { AnswerPass(data, Step(followUps(data, kyoto, today = "2025-11-14").first { it.id == FollowUpId.ROUTE }, kyoto), noop) }
    @Test fun answerCompare() = snap { AnswerPass(data, Step(followUps(data, kyoto, today = "2025-11-14").first { it.id == FollowUpId.COMPARE }, kyoto), noop) }
    @Test fun answerRouteReplaying() = snap {
        val route = followUps(data, kyoto, today = "2025-11-14").first { it.id == FollowUpId.ROUTE }.answer as Answer.Route
        val replay = rememberReplay(route.range).also { it.seek(route.range.first + (route.range.last - route.range.first) / 2) }
        RouteFigure(data, route, kyoto.tz, replay)
    }
    @Test fun answerCompareNoHome() = snap {
        val d = data.noHome(); val h = highlightFor(d, "2025-11-14")!!
        AnswerPass(d, Step(followUps(d, h, today = "2024-11-14").first { it.id == FollowUpId.COMPARE }, h), noop)
    }
    @Test fun answerDid() = snap { AnswerPass(data, Step(followUps(data, kyoto, today = "2025-11-14").first { it.id == FollowUpId.DID }, kyoto), noop) }
    @Test fun answerBackAndFarthest() = snap {
        val fus = followUps(data, kyoto, today = "2025-11-14").associateBy { it.id }
        AnswerPass(data, Step(fus.getValue(FollowUpId.BACK), kyoto), noop)
        AnswerPass(data, Step(fus.getValue(FollowUpId.FARTHEST), kyoto), noop)
    }
    @Test fun answerLastYear() = snap {
        val d = data.copy(daily = data.daily.map { if (it.date == "2022-11-15") it.copy(nNewPlaces = 0) else it })
        val h = highlightFor(d, "2025-11-15")!!
        AnswerPass(d, Step(followUps(d, h).first { it.id == FollowUpId.LAST_YEAR }, h), noop)
    }
}
