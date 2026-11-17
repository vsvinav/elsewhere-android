package com.elsewhere.app.ui.tabs.numbers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.analytics.DayType
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.Field
import com.elsewhere.app.ui.world.Legend
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.Share
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.WEEKDAYS
import com.elsewhere.app.ui.nav.Door
import com.elsewhere.app.ui.nav.DoorText
import com.elsewhere.app.ui.nav.door
import com.elsewhere.app.ui.nav.span
import com.elsewhere.app.ui.world.countUp
import com.elsewhere.app.ui.world.cv
import com.elsewhere.app.ui.world.date
import com.elsewhere.app.ui.world.hours
import com.elsewhere.app.data.modeLabel
import com.elsewhere.app.ui.world.month
import com.elsewhere.app.ui.world.n0
import com.elsewhere.app.ui.world.u
import kotlin.math.max
import kotlin.math.roundToInt

// Port of web/src/tabs/Stats/Stats.tsx: the dense strip of passes.

private val CLS = listOf(Poster.Red, Poster.Mustard, Poster.Ink)

@Composable
fun NumbersTab(vm: NumbersViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    ui?.let { NumbersScreen(it) } ?: Box(Modifier.fillMaxSize().background(Poster.Stock))
}

@Composable
fun NumbersScreen(ui: NumbersUi, modifier: Modifier = Modifier, scroll: Boolean = true) {
    Column(
        modifier.fillMaxWidth().background(Poster.Stock).then(if (scroll) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 12.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        MonthsPass(ui)
        RhythmPass(ui)
        DaysPass(ui)
        if (ui.hasWork) CommutePass(ui)
        ExplorePass(ui)
        CitiesPass(ui)
    }
}

/** The web's `.big`: a display figure with a small caps unit after it. */
@Composable
private fun Big(value: String, small: String? = null, ghost: Boolean = false, door: Door? = null) {
    Row(Modifier.padding(top = 18.dp).door(door), verticalAlignment = Alignment.Bottom) {
        Text(value.uppercase(), color = if (ghost) Poster.Ghost else Poster.Ink, style = PosterType.Display.copy(fontSize = 56.sp, lineHeight = 0.85.em, letterSpacing = (-0.01).em), softWrap = false)
        if (small != null) {
            Spacer(Modifier.width(8.dp))
            Text(small.uppercase(), Modifier.padding(bottom = 3.dp), style = PosterType.Display.copy(fontSize = 17.sp, letterSpacing = 0.02.em, lineHeight = 1.em))
        }
    }
}

@Composable
private fun Axis(left: String?, right: String?) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        CapsText(left ?: "", color = Poster.Ink3, size = 11.sp)
        CapsText(right ?: "", color = Poster.Ink3, size = 11.sp)
    }
}

/** Stacked columns, one per bucket, bottom segment first; heights are fractions of the tallest. */
@Composable
private fun Columns(stacks: List<List<Pair<Float, Color>>>, description: String, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 180.dp, doors: List<Door?> = emptyList()) {
    Box(modifier.fillMaxWidth().height(height)) {
        Bars(stacks, description, Modifier.matchParentSize())
        if (doors.isNotEmpty()) Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in stacks.indices) Box(Modifier.weight(1f).fillMaxHeight().door(doors.getOrNull(i)))
        }
    }
}

@Composable
private fun Bars(stacks: List<List<Pair<Float, Color>>>, description: String, modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = description }) {
        val n = max(1, stacks.size)
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        stacks.forEachIndexed { i, col ->
            var y = size.height
            for ((frac, c) in col) {
                val h = frac.coerceIn(0f, 1f) * size.height
                y -= h
                if (h > 0f) drawRect(c, Offset(i * (w + gap), y), Size(w, h))
            }
        }
        drawRect(Poster.Ink, Offset(0f, size.height - 6.dp.toPx()), Size(size.width, 6.dp.toPx()))
    }
}

@Composable
private fun MonthsPass(ui: NumbersUi) {
    val s = ui.stats; val u = ui.units
    Pass(share = Share("distance by month"), stub = {
        Field("Best month", ui.best?.let { countUp(u.cv(it.total)) }, big = true, door = ui.doors.bestMonth)
        Field("Which", ui.best?.let { month(it.key) }, door = ui.doors.which)
        Field("Average per month", countUp(u.cv(ui.avg)), big = true, door = ui.doors.average)
        Field("Months on record", "${s.months.size}", big = true, door = ui.doors.months)
    }) {
        Field("Distance by month", "Top three modes in colour, the rest in grey")
        Big(countUp(u.cv(ui.maxMonth)), "${u.u} peak", door = ui.doors.peakMonth)
        Spacer(Modifier.height(22.dp))
        val top = ui.modes
        Columns(
            s.months.map { m ->
                val other = m.total - top.sumOf { m.byMode[it] ?: 0.0 }
                listOf((other / ui.maxMonth).toFloat() to Poster.Stock2) + top.indices.reversed().map { i -> ((m.byMode[top[i]] ?: 0.0) / ui.maxMonth).toFloat() to CLS[i] }
            },
            "Distance per month, stacked by mode",
            doors = ui.doors.monthColumns,
        )
        Axis(s.months.firstOrNull()?.let { month(it.key) }, s.months.lastOrNull()?.let { month(it.key) })
        Spacer(Modifier.height(14.dp))
        Legend(top.mapIndexed { i, k -> modeLabel(k) to CLS[i] } + ("Other" to Poster.Stock2))
    }
}

@Composable
private fun RhythmPass(ui: NumbersUi) {
    val s = ui.stats
    Pass(share = Share("rhythm"), stub = {
        Field("Peak hour", ui.peak?.let { "${WEEKDAYS[it.d]} ${it.h.toString().padStart(2, '0')}:00" })
        Field("Busiest day out", if ((ui.byDay.getOrNull(ui.topDay) ?: 0.0) > 0) WEEKDAYS[ui.topDay] else null)
        Field("Hours away", countUp(ui.hoursAway), big = true, door = ui.doors.hoursAway)
    }) {
        Field("Weekly rhythm", "Hours away from home, by weekday and hour of day")
        Spacer(Modifier.height(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            s.rhythm.forEachIndexed { d, row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CapsText(WEEKDAYS[d], Modifier.width(36.dp), color = Poster.Ink3, size = 11.sp)
                    Spacer(Modifier.width(10.dp))
                    Canvas(Modifier.weight(1f).height(11.dp).semantics { contentDescription = "Hours away on ${WEEKDAYS[d]} by hour" }) {
                        val gap = 3.dp.toPx(); val w = (size.width - gap * 23) / 24
                        row.forEachIndexed { h, v ->
                            val x = h * (w + gap)
                            if (v == 0.0) drawRect(Poster.Stock2, Offset(x, 0f), Size(w, size.height))
                            else drawRect(Poster.Red, Offset(x, 0f), Size(w, size.height), alpha = max(0.08, v / s.rhythmMax).toFloat())
                        }
                    }
                }
            }
            Row {
                Spacer(Modifier.width(46.dp))
                Row(Modifier.weight(1f)) {
                    for (h in 0 until 24) Box(Modifier.weight(1f)) {
                        if (h % 3 == 0) Text(h.toString().padStart(2, '0'), color = Poster.Ink3, style = PosterType.Caps.copy(fontSize = 9.sp, letterSpacing = 0.em), softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun DaysPass(ui: NumbersUi) {
    val u = ui.units
    fun pct(t: DayType) = ui.pct(t)
    Pass(share = Share("kinds of days"), stub = {
        for (t in DayType.entries) Field(t.key.replaceFirstChar { it.uppercase() }, "${pct(t)}%", big = true, door = ui.doors.dayTypes[t])
    }) {
        Field("Kinds of days", "Away = over ${n0(u.cv(100.0))} ${u.u} from home · Work = 2 h+ at work · Out = 3 h+ elsewhere")
        Big(countUp((ui.stats.dayTypes[DayType.AWAY] ?: 0).toDouble()), "days away", door = ui.doors.daysAway)
        Spacer(Modifier.height(22.dp))
        val bands = buildList {
            add(Triple("Away", pct(DayType.AWAY), Poster.Red to Poster.Stock))
            if (ui.hasWork) add(Triple("Work", pct(DayType.WORK), Poster.Ink2 to Poster.Stock))
            add(Triple("Out", pct(DayType.OUT), Poster.Ink to Poster.Stock))
            add(Triple("Home", pct(DayType.HOME), Poster.Mustard to Poster.Ink))
        }.filter { it.second > 0 }
        Row(Modifier.fillMaxWidth().height(40.dp).border(2.dp, Poster.Ink).padding(2.dp)) {
            for ((name, p, c) in bands) Box(Modifier.weight(p.toFloat()).fillMaxSize().background(c.first).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                Text(name.uppercase(), color = c.second, style = PosterType.Display.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.1.em), maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
            }
        }
    }
}

@Composable
private fun CommutePass(ui: NumbersUi) {
    val c = ui.commute
    Pass(share = Share("commute"), stub = {
        Field("Legs on record", countUp(c.legs.size.toDouble()), big = true, door = ui.doors.legs)
        Field("To work · median", if (c.toWork.n > 0) "${c.toWork.medianMin.roundToInt()} min" else null, door = ui.doors.toWork.takeIf { c.toWork.n > 0 })
        Field("To home · median", if (c.toHome.n > 0) "${c.toHome.medianMin.roundToInt()} min" else null, door = ui.doors.toHome.takeIf { c.toHome.n > 0 })
        Field("Bad day (p90)", if (c.legs.isNotEmpty()) "${max(c.toWork.p90Min, c.toHome.p90Min).roundToInt()} min" else null, door = ui.doors.badDay.takeIf { c.legs.isNotEmpty() })
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Field("Commute", "Home ↔ work legs under 3 hours", Modifier.weight(1f))
            Field("Usually by", ui.usualMode?.let(::modeLabel))
        }
        if (c.legs.isEmpty()) Big("—", ghost = true) else {
            Big(countUp(c.medianAll), "min door to door", door = ui.doors.median)
            Spacer(Modifier.height(14.dp))
            Legend(listOf("Leaving home" to Poster.Red, "Leaving work" to Poster.Ink))
            Spacer(Modifier.height(14.dp))
            Spark(c.toWork.byHour, ui.hourMax, Poster.Red)
            Spacer(Modifier.height(2.dp))
            Spark(c.toHome.byHour, ui.hourMax, Poster.Ink)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                for (h in listOf("00", "06", "12", "18", "23")) CapsText(h, color = Poster.Ink3, size = 11.sp)
            }
        }
    }
}

@Composable
private fun Spark(values: List<Int>, maxV: Int, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        val gap = 2.dp.toPx(); val w = (size.width - gap * 23) / 24
        values.forEachIndexed { h, v ->
            val bh = max(1f, size.height * v / maxV)
            drawRect(color, Offset(h * (w + gap), size.height - bh), Size(w, bh))
        }
        drawRect(Poster.Ink, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx()))
    }
}

@Composable
private fun ExplorePass(ui: NumbersUi) {
    val e = ui.explore
    Pass(share = Share("exploration"), stub = {
        Field("New places", countUp(ui.newTotal.toDouble()), big = true, door = ui.doors.newPlaces)
        Field("Per month", countUp(ui.newTotal / max(1, e.size).toDouble()), big = true, door = ui.doors.perMonth)
        Field("Lapsed", countUp(ui.lapsedTotal.toDouble()), big = true, door = ui.doors.lapsed)
    }) {
        Field("Exploration", "First visits, returns, and places that went quiet, by month")
        Spacer(Modifier.height(22.dp))
        val m = ui.maxExplore.toFloat()
        Columns(e.map { b -> listOf(b.lapsed / m to Poster.Stock2, b.repeat / m to Poster.Mustard, b.new / m to Poster.Red) }, "New, repeat and lapsed places per month", doors = ui.doors.exploreColumns)
        Axis(e.firstOrNull()?.let { month(it.key) }, e.lastOrNull()?.let { month(it.key) })
        Spacer(Modifier.height(14.dp))
        Legend(listOf("New" to Poster.Red, "Repeat" to Poster.Mustard, "Lapsed" to Poster.Stock2))
    }
}

@Composable
private fun CitiesPass(ui: NumbersUi) {
    val cities = ui.stats.cities
    Pass(share = Share("cities")) {
        Field("Cities", listOf(span("${cities.size} cities", ui.doors.cityCount), span(" in "), span("${ui.countries} countries", ui.doors.countryCount), span(", by time spent")))
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).background(Poster.Ink))
        cities.take(25).forEachIndexed { i, c ->
            val d = ui.doors.cities.getOrNull(i)
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(c.city.uppercase(), Modifier.weight(1f, fill = false).door(d?.city), style = PosterType.Display.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!c.country.isNullOrEmpty()) { Spacer(Modifier.width(6.dp)); CapsText(c.country, Modifier.padding(bottom = 2.dp), color = Poster.Ink3, size = 12.sp) }
                    Spacer(Modifier.width(16.dp))
                    Text(hours(c.hours).uppercase(), Modifier.door(d?.hours), style = PosterType.Display.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.em))
                }
                Spacer(Modifier.height(4.dp))
                DoorText(
                    listOf(span("${n0(c.visits)} visits", d?.visits), span(" · "), span("${n0(c.places)} places", d?.places), span(" · "), span(date(c.first), d?.first), span(" → "), span(date(c.last), d?.last)),
                    color = Poster.Ink3, style = PosterType.Label.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Poster.Ink))
        }
        if (cities.size > 25) CapsText("and ${cities.size - 25} more", Modifier.padding(top = 18.dp), color = Poster.Red, size = 12.sp)
    }
}
