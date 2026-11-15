package com.elsewhere.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elsewhere.app.data.Units
import com.elsewhere.app.ui.shell.DatePickSheet
import com.elsewhere.app.ui.shell.Masthead
import com.elsewhere.app.ui.shell.RangeBar
import com.elsewhere.app.ui.shell.RangeViewModel
import com.elsewhere.app.ui.shell.ShellViewModel
import com.elsewhere.app.ui.shell.TicketStrip
import com.elsewhere.app.ui.shell.dayOf
import com.elsewhere.app.ui.tabs.dates.DatesTab
import com.elsewhere.app.ui.tabs.facts.FactsTab
import com.elsewhere.app.ui.tabs.numbers.NumbersTab
import com.elsewhere.app.ui.tabs.stops.StopsTab
import com.elsewhere.app.ui.tabs.tour.TourTab
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.Poster

private enum class Picking { FROM, TO }

/** The section each ticket opens on. Flights, Map and Replay still print a placeholder. */
@Composable
fun TabContent(tab: Tab, go: (Tab) -> Unit) = when (tab) {
    Tab.TOUR -> TourTab(onGo = go)
    Tab.NUMBERS -> NumbersTab()
    Tab.FACTS -> FactsTab()
    Tab.STOPS -> StopsTab()
    Tab.DATES -> DatesTab()
    else -> Placeholder(tab)
}

/**
 * The printed app: masthead on top, range bar and ticket strip below, one section in between.
 * `content` renders a tab; tests pass a stand-in so no section needs its own injected ViewModel.
 */
@Composable
fun AppScaffold(
    onChangeAnswers: () -> Unit, onMapsKey: () -> Unit,
    shell: ShellViewModel = hiltViewModel(), rangeVm: RangeViewModel = hiltViewModel(),
    content: @Composable (tab: Tab, go: (Tab) -> Unit) -> Unit = { t, g -> TabContent(t, g) },
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val tab = Tab.byRoute(entry?.destination?.route)
    var selected by rememberSaveable { mutableStateOf(Tab.TOUR) }
    val go = { t: Tab ->
        selected = t
        nav.navigate(t.route) { popUpTo(Tab.TOUR.route) { inclusive = t == Tab.TOUR }; launchSingleTop = true }
    }
    BackHandler(enabled = tab != Tab.TOUR) { go(Tab.TOUR) }

    val profile by shell.profile.collectAsStateWithLifecycle()
    val counters by shell.counters.collectAsStateWithLifecycle()
    val range by rangeVm.range.collectAsStateWithLifecycle()
    val bounds by rangeVm.bounds.collectAsStateWithLifecycle()
    val years by rangeVm.years.collectAsStateWithLifecycle()
    val from = dayOf(range.first); val to = dayOf(range.last)
    var picking by remember { mutableStateOf<Picking?>(null) }

    Scaffold(
        containerColor = Poster.Stock,
        topBar = {
            Masthead(
                name = profile?.name, units = profile?.units ?: Units.KM, counters = counters,
                onHome = { go(Tab.TOUR) }, onChangeAnswers = onChangeAnswers, onMapsKey = onMapsKey,
                onToggleUnits = shell::toggleUnits, onStartOver = shell::reset,
            )
        },
        bottomBar = {
            Column {
                RangeBar(from, to, years, rangeVm::isPreset, onFrom = { picking = Picking.FROM }, onTo = { picking = Picking.TO }, onPreset = rangeVm::preset)
                TicketStrip(selected = tab, onSelect = go, onBrand = { go(Tab.TOUR) })
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Tab.TOUR.route, Modifier.fillMaxSize().padding(pad)) {
            for (t in Tab.entries) composable(t.route) { content(t, go) }
        }
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

@Composable
fun Placeholder(tab: Tab) {
    Box(Modifier.fillMaxSize().padding(20.dp)) { Pass { DisplayText(tab.label, 48.sp, misreg = true) } }
}
