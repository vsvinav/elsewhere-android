package com.elsewhere.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elsewhere.app.ui.shell.DatePickSheet
import com.elsewhere.app.ui.shell.Masthead
import com.elsewhere.app.ui.shell.RangeBar
import com.elsewhere.app.ui.shell.RangeViewModel
import com.elsewhere.app.ui.shell.ShellViewModel
import com.elsewhere.app.ui.shell.TicketStrip
import com.elsewhere.app.ui.shell.dayOf
import com.elsewhere.app.ui.tabs.dates.DatesTab
import com.elsewhere.app.ui.tabs.facts.FactsTab
import com.elsewhere.app.ui.tabs.flights.FlightsTab
import com.elsewhere.app.ui.tabs.map.MapTab
import com.elsewhere.app.ui.tabs.numbers.NumbersTab
import com.elsewhere.app.ui.tabs.replay.ReplayTab
import com.elsewhere.app.ui.tabs.stops.StopsTab
import com.elsewhere.app.ui.tabs.tour.TourTab
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.NamingStamp
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.ReadyBar

private enum class Picking { FROM, TO }

/** The section each ticket opens on. */
@Composable
fun TabContent(tab: Tab, go: (Tab) -> Unit, onRecord: () -> Unit = {}) = when (tab) {
    Tab.TOUR -> TourTab(onGo = go, onRecord = onRecord)
    Tab.NUMBERS -> NumbersTab()
    Tab.FACTS -> FactsTab()
    Tab.STOPS -> StopsTab()
    Tab.DATES -> DatesTab()
    Tab.FLIGHTS -> FlightsTab()
    Tab.MAP -> MapTab()
    Tab.REPLAY -> ReplayTab()
}

/**
 * The printed app: masthead on top, range bar and ticket strip below, one section in between.
 * `content` renders a tab; tests pass a stand-in so no section needs its own injected ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    onChangeAnswers: () -> Unit, onSettings: () -> Unit, onRecord: () -> Unit = {},
    shell: ShellViewModel = hiltViewModel(), rangeVm: RangeViewModel = hiltViewModel(),
    content: @Composable (tab: Tab, go: (Tab) -> Unit) -> Unit = { t, g -> TabContent(t, g, onRecord) },
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val tab = Tab.byRoute(entry?.destination?.route)
    var selected by rememberSaveable { mutableStateOf(Tab.TOUR) }
    val go = { t: Tab ->
        selected = t
        // Returning home pops back to the live Tour entry so its view model and scroll survive.
        if (t == Tab.TOUR) { if (!nav.popBackStack(Tab.TOUR.route, inclusive = false)) nav.navigate(t.route) { launchSingleTop = true } }
        else nav.navigate(t.route) { popUpTo(Tab.TOUR.route); launchSingleTop = true }
    }
    BackHandler(enabled = tab != Tab.TOUR) { go(Tab.TOUR) }

    val profile by shell.profile.collectAsStateWithLifecycle()
    val counters by shell.counters.collectAsStateWithLifecycle()
    val naming by shell.naming.collectAsStateWithLifecycle()
    val ready by shell.ready.collectAsStateWithLifecycle()
    val refreshing by shell.refreshing.collectAsStateWithLifecycle()
    val range by rangeVm.range.collectAsStateWithLifecycle()
    val bounds by rangeVm.bounds.collectAsStateWithLifecycle()
    val years by rangeVm.years.collectAsStateWithLifecycle()
    val from = dayOf(range.first); val to = dayOf(range.last)
    var picking by remember { mutableStateOf<Picking?>(null) }

    Box {
    Scaffold(
        containerColor = Poster.Stock,
        topBar = {
            Masthead(
                name = profile?.name, counters = counters,
                onHome = { go(Tab.TOUR) }, onChangeAnswers = onChangeAnswers, onSettings = onSettings, onRecord = onRecord,
                onStartOver = shell::reset,
            )
        },
        bottomBar = {
            Column {
                RangeBar(from, to, years, rangeVm::isPreset, onFrom = { picking = Picking.FROM }, onTo = { picking = Picking.TO }, onPreset = rangeVm::preset)
                TicketStrip(selected = tab, onSelect = go, onBrand = { go(Tab.TOUR) })
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            // One pull-to-refresh for every tab; it only takes the overscroll a scrolling child hands up, so the maps keep their pan.
            val pull = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = refreshing, onRefresh = shell::refresh, state = pull, modifier = Modifier.fillMaxSize(),
                indicator = { PullToRefreshDefaults.Indicator(pull, refreshing, Modifier.align(Alignment.TopCenter), containerColor = Poster.Stock, color = Poster.Mustard) },
            ) {
                NavHost(nav, startDestination = Tab.TOUR.route, Modifier.fillMaxSize()) {
                    for (t in Tab.entries) composable(t.route) { content(t, go) }
                }
            }
            NamingStamp(naming, Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
    }
    // Along the very top edge, over the masthead, as the web fixes `.ready` at top 0.
    ReadyBar(ready, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars))
    }

    val p = picking
    if (p != null && bounds.last > 0) {
        // From can't pass To and To can't precede From: the far end bounds the calendar, as in RangeBar.tsx.
        DatePickSheet(
            label = if (p == Picking.FROM) "From" else "To",
            value = if (p == Picking.FROM) from else to,
            min = if (p == Picking.FROM) dayOf(bounds.first) else from,
            max = if (p == Picking.FROM) to else dayOf(bounds.last),
            other = if (p == Picking.FROM) to else from,
            onPick = { d -> if (p == Picking.FROM) rangeVm.setFrom(d) else rangeVm.setTo(d) },
            onDismiss = { picking = null },
        )
    }
}
