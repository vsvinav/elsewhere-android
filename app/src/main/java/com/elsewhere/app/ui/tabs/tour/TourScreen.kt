package com.elsewhere.app.ui.tabs.tour

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.analytics.Lens
import com.elsewhere.app.analytics.lensOf
import com.elsewhere.app.data.Units
import com.elsewhere.app.share.LocalPrinting
import com.elsewhere.app.ui.Tab
import com.elsewhere.app.ui.world.BigNum
import com.elsewhere.app.ui.world.Bill
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DateRow
import com.elsewhere.app.ui.world.DatesList
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Field
import com.elsewhere.app.ui.world.Foot
import com.elsewhere.app.ui.world.Lede
import com.elsewhere.app.ui.world.NightsBill
import com.elsewhere.app.ui.world.PTable
import com.elsewhere.app.ui.world.PlaceSticker
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.RecordStamp
import com.elsewhere.app.ui.shell.YearWheel
import com.elsewhere.app.ui.world.SetlistRow
import com.elsewhere.app.ui.world.Share
import com.elsewhere.app.ui.world.Tone
import com.elsewhere.app.ui.world.TrailMap
import com.elsewhere.app.ui.world.Word
import com.elsewhere.app.ui.world.countUp
import com.elsewhere.app.ui.world.cv
import com.elsewhere.app.ui.world.date
import com.elsewhere.app.ui.world.dateISO
import com.elsewhere.app.ui.world.label
import com.elsewhere.app.ui.world.n0
import com.elsewhere.app.ui.world.plural
import com.elsewhere.app.analytics.toFixed
import com.elsewhere.app.ui.world.u
import kotlin.math.max
import kotlin.math.roundToInt

// Port of web/src/tabs/Home/Home.tsx: the poster, a stack of bills you peel through.

@Composable
fun TourTab(onGo: (Tab) -> Unit, onRecord: () -> Unit = {}, vm: TourViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val window by vm.window.collectAsStateWithLifecycle()
    ui?.let { TourScreen(it, onGo, onSeen = vm::seen, onRecord = onRecord, window = window, onRange = vm::setRange) } ?: Box(Modifier.fillMaxSize().background(Poster.Stock))
}

private fun lensColor(mode: String) = when (lensOf(mode)) { Lens.FLY -> Poster.Red; Lens.FOOT -> Poster.Mustard; else -> Poster.Ink }

/** `scroll = false` lays the whole poster out at its full height, for snapshots. */
@Composable
fun TourScreen(
    ui: TourUi, onGo: (Tab) -> Unit, modifier: Modifier = Modifier, scroll: Boolean = true, onSeen: (String) -> Unit = {}, onRecord: () -> Unit = {},
    window: LongRange = ui.record.meta.range, onRange: (LongRange) -> Unit = {},
) {
    Column(modifier.fillMaxWidth().background(Poster.Stock).then(if (scroll) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier)) {
        TourHero(ui, onGo, onRecord, window, onRange)
        ui.nights?.let { NightsBill(it) }
        HeadlineBill(ui, onGo)
        SetlistBill(ui)
        ResidencyBill(ui, onSeen)
        RecordsBill(ui)
        BackstageBill(ui)
    }
}

private fun TourUi.countries() = "${n0(facts.countries)} ${plural(facts.countries, "country", "countries")}"

@Composable
internal fun TourHero(ui: TourUi, onGo: (Tab) -> Unit, onRecord: () -> Unit = {}, window: LongRange = ui.record.meta.range, onRange: (LongRange) -> Unit = {}) {
    val f = ui.facts; val t = ui.tour; val u = ui.units
    Bill(first = true, share = Share(if (ui.name.isNotEmpty()) "${ui.name}'s elsewhere" else "elsewhere ${ui.yearSpan}")) {
        val printing = LocalPrinting.current
        Box(Modifier.fillMaxWidth()) {
            Column {
                if (ui.name.isNotEmpty()) {
                    DisplayText("${ui.name}'s", 72.sp, misreg = true)
                    DisplayText("Elsewhere", 72.sp, color = Poster.Red, misreg = true)
                } else {
                    DisplayText("Else", 96.sp, misreg = true)
                    DisplayText("where", 96.sp, color = Poster.Red, misreg = true)
                }
            }
            RecordStamp("Official record", "${t.years.second}", if (ui.deviceDays > 0) "recording" else "live", Modifier.align(Alignment.TopEnd), size = 92.dp)
        }
        if (!printing) {
            Spacer(Modifier.height(22.dp))
            YearWheel(ui.bounds, window, onRange)
        }
        Spacer(Modifier.height(28.dp))
        val counting = if (ui.deviceDays > 0) " …and still counting: ${n0(ui.deviceDays)} ${plural(ui.deviceDays, "day")} recorded on this phone." else ""
        Lede("${ui.yearSpan}, live from ${n0(f.days)} days of record. Everywhere your phone slept, every leg between. ${n0(f.places)} places, ${n0(f.cities)} cities, ${ui.countries()}.$counting")
        Spacer(Modifier.height(40.dp))
        TrailMap(ui.trail)
        Spacer(Modifier.height(40.dp))
        DatesList(
            t.stops.take(6).map { DateRow(it.city, it.country, "${it.nights}", plural(it.nights, "night")) },
            more = when {
                printing -> null
                t.stops.size > 6 -> ({ CapsText("+ ${t.stops.size - 6} more cities →", Modifier.clickable(role = Role.Button) { onGo(Tab.DATES) }, size = 12.sp) })
                t.stops.isEmpty() -> ({ CapsText("No nights away on record", size = 12.sp) })
                else -> null
            },
        )
        Spacer(Modifier.height(40.dp))
        Foot("${n0(f.cities)} cities · ${ui.countries()}", "${countUp(u.cv(f.totalKm))} ${u.u}")
        if (!printing) {
            Spacer(Modifier.height(20.dp))
            CapsText(if (ui.deviceDays > 0) "Recording on this phone →" else "Record new travel →", Modifier.clickable(role = Role.Button, onClick = onRecord), color = Poster.Red, size = 12.sp)
        }
    }
}

@Composable
internal fun HeadlineBill(ui: TourUi, onGo: (Tab) -> Unit) {
    val act = ui.tour.headline ?: return
    val f = ui.facts; val t = ui.tour; val u = ui.units
    Bill(Tone.INK, share = Share("headline ${act.city}")) {
        CapsText("Headline act · farthest from home")
        Spacer(Modifier.height(16.dp))
        Word(act.city)
        Spacer(Modifier.height(16.dp))
        Lede("${n0(u.cv(act.km))} ${u.u} from home. ${act.nights} ${plural(act.nights, "night")}, ${date(act.start)} → ${date(act.end)}.")
        Spacer(Modifier.height(40.dp))
        DatesList(buildList {
            f.longestTrip?.let { add(DateRow("Longest trip", it.trip.anchorCities.take(2).joinToString(" · ").ifEmpty { "away" }, "${it.trip.nights}", "nights")) }
            add(DateRow("Nights away", null, n0(t.nightsAway)))
            add(DateRow("Nights home", null, n0(t.nightsHome)))
            add(DateRow("Trips", null, "${f.trips}"))
        })
        Spacer(Modifier.height(48.dp))
        Box(Modifier.fillMaxWidth().padding(end = 12.dp), contentAlignment = Alignment.CenterEnd) {
            PlaceSticker(
                ui.actPlace, ui.actName ?: act.city, ui.actSub, Modifier.fillMaxWidth(0.78f), tilt = 2f, onClick = { onGo(Tab.STOPS) },
                stamp = { RecordStamp("", "${toFixed((u.cv(act.km) / 100).roundToInt() / 10.0, 1).removeSuffix(".0")}k", "${u.u} out", size = 84.dp) },
            )
        }
    }
}

@Composable
internal fun SetlistBill(ui: TourUi) {
    val f = ui.facts; val u = ui.units
    Bill(share = Share("setlist")) {
        CapsText("Setlist · ${if (u == Units.MI) "miles" else "kilometres"} by mode")
        Spacer(Modifier.height(16.dp))
        BigNum(countUp(u.cv(f.totalKm)), u.u)
        Spacer(Modifier.height(28.dp))
        val top = f.kmByMode.firstOrNull()?.second ?: 1.0
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for ((m, km) in f.kmByMode) SetlistRow(label(m), (km / top).toFloat(), lensColor(m), n0(u.cv(km)), u.u)
        }
        Spacer(Modifier.height(40.dp))
        Foot("${toFixed(f.totalKm / 40075, 1)}× around the Earth", "${f.flights} ${plural(f.flights, "flight")}")
    }
}

@Composable
internal fun ResidencyBill(ui: TourUi, onSeen: (String) -> Unit = {}) {
    val f = ui.facts
    val hoursTotal = f.hours.home + f.hours.work + f.hours.other + f.hours.transit
    fun pct(h: Double) = (100 * h / max(1.0, hoursTotal)).roundToInt()
    Bill(Tone.RED, share = Share("residency")) {
        CapsText("Residency · where the hours went")
        Spacer(Modifier.height(16.dp))
        BigNum(countUp(f.hours.other), "h out")
        Spacer(Modifier.height(16.dp))
        Lede("${pct(f.hours.home)}% home · ${if (ui.hasWork) "${pct(f.hours.work)}% work · " else ""}${pct(f.hours.other)}% elsewhere · ${pct(f.hours.transit)}% moving. About ${toFixed(f.hours.other / max(1, f.days).toDouble(), 1)} hours a day somewhere else.")
        if (f.thirdPlaces.isNotEmpty()) {
            Spacer(Modifier.height(40.dp))
            DatesList(f.thirdPlaces.map { DateRow(it.name, it.place.city, "${it.place.nVisits}", "visits", id = it.id.takeIf { _ -> it.place.name == null || it.place.photo == null }) }, onSeen = onSeen)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecordsBill(ui: TourUi) {
    val f = ui.facts; val u = ui.units
    Bill(share = Share("records")) {
        CapsText("Records · personal bests")
        Spacer(Modifier.height(24.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            f.records.longestDay?.let { RecordStamp("Longest day", n0(u.cv(it.km)), "${u.u} · ${dateISO(it.date).take(6)}", color = Poster.Ink, size = 112.dp) }
            f.records.busiestDay?.let { RecordStamp("Busiest day", "${it.places}", "places · ${dateISO(it.date).take(6)}", size = 112.dp) }
            f.records.farthest?.let { RecordStamp("Farthest", n0(u.cv(it.km)), "${u.u} · ${it.city ?: "away"}", color = Poster.Ink, size = 112.dp) }
        }
        Spacer(Modifier.height(22.dp))
        PTable(listOf("Year", u.u, "Places", "Trips", "Flights"), f.years.map { y -> listOf("${y.year}", n0(u.cv(y.km)), n0(y.places), "${y.trips}", "${y.flights}") })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BackstageBill(ui: TourUi) {
    val f = ui.facts
    Bill(Tone.INK) {
        CapsText("Backstage · your record, your key")
        Spacer(Modifier.height(24.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Field("Name on the bill", ui.name.ifEmpty { "—" })
            Field("Home", ui.homeCity ?: "—")
            Field("Record", "${date(f.range.first)} → ${date(f.range.last)}")
        }
        Spacer(Modifier.height(28.dp))
        val rep = ui.record.meta.importReport
        FlowRow(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Field("Source", "Your export · ${ui.record.meta.sourceFile}")
            Field("Segments", "${n0(rep.parsed)} parsed · ${n0(rep.segmentsTotal - rep.parsed)} skipped")
            Field("Kept", "On this phone only")
            Field("Named", "${ui.named} places" + if (ui.photos > 0) " · ${ui.photos} with photos" else "")
        }
        Spacer(Modifier.height(28.dp))
        Text("Nothing is uploaded. Your record stays on this phone.", color = Poster.Stock3, style = PosterType.Label.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}
