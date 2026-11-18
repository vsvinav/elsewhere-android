package com.elsewhere.app.ui.tabs.replay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.analytics.Grain
import com.elsewhere.app.analytics.LENSES
import com.elsewhere.app.analytics.Lens
import com.elsewhere.app.analytics.Rail
import com.elsewhere.app.analytics.fmtTime
import com.elsewhere.app.analytics.lensOf
import com.elsewhere.app.analytics.placeName
import com.elsewhere.app.analytics.visitAt
import com.elsewhere.app.analytics.whereaboutsAt
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Record
import com.elsewhere.app.map.Arrival
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.PlacePhoto
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.data.modeLabel
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoField
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

// Port of web/src/map/Hud.tsx and ArrivalTags.tsx.

private val WEEKDAY = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
private val MONTH = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

/** What the HUD's stub prints for one playhead: the local date and where you are. */
data class HudInfo(
    val dow: String, val day: String, val monthYear: String, val time: String,
    /** True inside a stop: the label reads "At"; otherwise "Now" with the ink of the movement. */
    val atStop: Boolean, val where: String, val sub: String, val ink: Color?,
    /** Minutes east of UTC where the playhead stands, so the headline prints the same local date. */
    val tz: Int = 0,
)

fun hudInfo(data: Record, playhead: Long): HudInfo {
    val w = whereaboutsAt(data, playhead)
    val visit = if (w?.mode == "STILL") visitAt(data, playhead) else null
    val tz = visit?.tz ?: data.activities.firstOrNull { it.start <= playhead && playhead <= it.end }?.tz ?: 0
    val d = Instant.ofEpochSecond(playhead + tz * 60L).atOffset(ZoneOffset.UTC)
    val place = visit?.let { data.places[it.placeId] }
    return HudInfo(
        dow = WEEKDAY[d.get(ChronoField.DAY_OF_WEEK) % 7], day = d.dayOfMonth.toString().padStart(2, '0'),
        monthYear = "${MONTH[d.monthValue - 1]} ${d.year}", time = fmtTime(playhead, tz),
        atStop = visit != null,
        where = if (visit != null) placeName(place, visit.placeId) else w?.let { modeLabel(it.mode) } ?: "—",
        sub = if (visit != null) place?.city ?: "city not on record" else if (w != null) "in motion" else "no record",
        ink = if (visit == null && w != null) ink(lensOf(w.mode)) else null,
        tz = tz,
    )
}

private val MONTH_FULL = listOf("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER")

/** The headline over the strip: the playhead named at the grain in force, a year, a month or a day. */
fun headline(grain: Grain, playhead: Long, tz: Int): String {
    val d = Instant.ofEpochSecond(playhead + tz * 60L).atOffset(ZoneOffset.UTC)
    return when (grain) {
        Grain.YEAR -> d.year.toString()
        Grain.MONTH -> "${MONTH_FULL[d.monthValue - 1]} ${d.year}"
        Grain.DAY -> "${WEEKDAY[d.get(ChronoField.DAY_OF_WEEK) % 7]} ${d.dayOfMonth.toString().padStart(2, '0')} ${MONTH[d.monthValue - 1]} ${d.year}"
    }
}

fun ink(lens: Lens): Color = Color(android.graphics.Color.parseColor(lens.color))

/** The speed slider's log scale: 2^-1 … 2^5, as the web's `Math.log2(speed)` range input. */
const val SPEED_LOG_MIN = -1f
const val SPEED_LOG_MAX = 5f
fun speedToSlider(speed: Double): Float = (ln(speed) / ln(2.0)).toFloat().coerceIn(SPEED_LOG_MIN, SPEED_LOG_MAX)
fun sliderToSpeed(v: Float): Double = 2.0.pow(((v * 10).roundToInt() / 10f).toDouble())
fun speedLabel(speed: Double): String = if (speed < 10) "%.1f×".format(java.util.Locale.US, speed) else "${speed.roundToInt()}×"

private val ChipStyle get() = PosterType.Display.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.03.em, lineHeight = 1.em)

/** The kinds of movement, one on at a time: Play cuts from leg to leg under a lens, the map draws only that ink. */
@Composable
fun LensChips(lens: Lens, onLens: (Lens) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp)
            .selectableGroup().semantics { contentDescription = "Kind of movement" },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        for (l in LENSES) {
            val on = l == lens
            Row(
                Modifier.background(if (on) Poster.Ink else Poster.Stock).border(2.dp, Poster.Ink)
                    .selectable(selected = on, role = Role.RadioButton) { onLens(l) }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (l != Lens.ALL) {
                    Box(Modifier.size(9.dp).background(ink(l)).border(1.dp, if (on) Poster.Stock else Poster.Ink))
                    Spacer(Modifier.width(6.dp))
                }
                Text(l.label.uppercase(), color = if (on) Poster.Stock else Poster.Ink, style = ChipStyle, softWrap = false)
            }
        }
    }
}

/** Year, Month, Day: the grain the map draws at; dead when the window has nothing to show at it. */
@Composable
fun GrainChips(grain: Grain, allowed: List<Grain>, onGrain: (Grain) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(Modifier.selectableGroup().semantics { contentDescription = "Time grain" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (g in Grain.entries) {
            val on = g == grain; val enabled = g in allowed
            Text(
                g.label.uppercase(), color = if (on) Poster.Stock else if (enabled) Poster.Ink else Poster.Ghost, style = ChipStyle, softWrap = false,
                modifier = Modifier.background(if (on) Poster.Ink else Poster.Stock).border(2.dp, if (enabled) Poster.Ink else Poster.Ghost)
                    .selectable(selected = on, enabled = enabled, role = Role.RadioButton) { onGrain(g); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
    }
}

/**
 * The stub under the map: the date, where you are, play and speed; the rail while a finger holds the strip; then the
 * grain chips and headline over the scrub with its years.
 */
@Composable
fun ReplayHud(
    info: HudInfo, range: LongRange, playhead: Double, playing: Boolean, speed: Double, follow: Boolean,
    grain: Grain, allowed: List<Grain>, scrubbing: Boolean, rail: Rail, noHome: Boolean = false,
    onPlay: () -> Unit, onSpeed: (Double) -> Unit, onScrub: (Double) -> Unit, onFollow: () -> Unit,
    onGrain: (Grain) -> Unit, onScrubbing: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateBlock(info)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (info.ink != null) { Box(Modifier.size(8.dp).background(info.ink)); Spacer(Modifier.width(5.dp)) }
                    Text(if (info.atStop) "AT" else "NOW", color = Poster.Ink3, style = PosterType.Caps.copy(fontSize = 10.sp))
                }
                Text(
                    info.where.uppercase(), color = Poster.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = PosterType.Display.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 0.95.em),
                )
                Text(info.sub, color = Poster.Ink3, style = PosterType.Label.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            PlayButton(playing, onPlay)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(speedLabel(speed), color = Poster.Ink, style = PosterType.Display.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), modifier = Modifier.width(52.dp))
            PosterSlider(
                speedToSlider(speed), { onSpeed(sliderToSpeed(it)) }, SPEED_LOG_MIN..SPEED_LOG_MAX,
                Modifier.weight(1f).semantics { contentDescription = "Replay speed" },
            )
            if (!follow) { Spacer(Modifier.width(8.dp)); InkButton("Follow", onFollow, quiet = true) }
        }
        Rail(rail, visible = scrubbing, noHome = noHome, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            GrainChips(grain, allowed, onGrain)
            Spacer(Modifier.weight(1f))
            Text(
                headline(grain, (if (playhead.isNaN()) range.first.toDouble() else playhead).toLong(), info.tz), color = Poster.Ink,
                style = PosterType.Caps.copy(fontSize = 11.sp), maxLines = 1, softWrap = false,
            )
        }
        Scrub(range, playhead, onScrub, onScrubbing) { dir ->
            val i = allowed.indexOf(grain); val j = (i + dir).coerceIn(0, allowed.lastIndex)
            if (j != i) onGrain(allowed[j])
        }
    }
}

@Composable
private fun DateBlock(info: HudInfo) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.border(2.dp, Poster.Ink).padding(horizontal = 10.dp, vertical = 6.dp).width(64.dp)) {
        Text(info.dow, color = Poster.Red, style = PosterType.Caps.copy(fontSize = 10.sp))
        Text(info.day, color = Poster.Ink, style = PosterType.Display.copy(fontSize = 40.sp, fontWeight = FontWeight.Black, lineHeight = 0.9.em))
        Text(info.monthYear, color = Poster.Ink, style = PosterType.Caps.copy(fontSize = 10.sp, letterSpacing = 0.08.em), maxLines = 1, softWrap = false)
        Text(info.time, color = Poster.Ink3, style = PosterType.Label.copy(fontSize = 11.sp))
    }
}

/** The web's `.play`: an ink square with a stock glyph, a triangle to play and two bars to pause. */
@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    Canvas(
        Modifier.size(52.dp).background(Poster.Ink).border(2.dp, Poster.Ink)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = if (playing) "Pause" else "Play" },
    ) {
        val u = size.minDimension / 16f
        if (playing) {
            drawRect(Poster.Stock, Offset(4 * u, 4 * u), Size(3 * u, 8 * u)); drawRect(Poster.Stock, Offset(9 * u, 4 * u), Size(3 * u, 8 * u))
        } else {
            val p = Path().apply { moveTo(5 * u, 3.5f * u); lineTo(13 * u, 8 * u); lineTo(5 * u, 12.5f * u); close() }
            drawPath(p, Poster.Stock, style = Fill)
        }
    }
}

@Composable
fun PosterSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier) {
    Slider(
        value, onChange, modifier, valueRange = range,
        colors = SliderDefaults.colors(thumbColor = Poster.Ink, activeTrackColor = Poster.Red, inactiveTrackColor = Poster.Stock3, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
    )
}

/** Luggage tags that print when the playhead arrives somewhere new. */
@Composable
fun ArrivalTags(tags: List<Arrival>, places: Map<String, Place>, modifier: Modifier = Modifier) {
    Column(modifier.width(168.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (t in tags) ArrivalTag(t, places[t.placeId])
    }
}

@Composable
private fun ArrivalTag(t: Arrival, p: Place?) {
    val motion = LocalMotion.current
    val shown by animateFloatAsState(if (t.leaving) 0f else 1f, if (motion) tween(420) else snap(), label = "tag")
    Box(Modifier.alpha(shown).background(Color.White).border(2.dp, Poster.Ink)) {
        Column(Modifier.padding(8.dp)) {
            PlacePhoto(p)
            Spacer(Modifier.height(4.dp))
            Text(if (t.nth == 1) "FIRST VISIT" else "VISIT Nº ${t.nth}", color = Poster.Red, style = PosterType.Caps.copy(fontSize = 9.sp))
            Text(
                placeName(p, t.placeId).uppercase(), color = Poster.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = PosterType.Display.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 0.95.em),
            )
            Text(
                listOfNotNull(p?.city, p?.countryCode).joinToString(", ").ifEmpty { "city not on record" },
                color = Poster.Ink3, style = PosterType.Label.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        // The strip of tape that holds the tag down; a ghost of it when the place has no name on record.
        Box(Modifier.align(Alignment.TopCenter).width(40.dp).height(10.dp).background(if (p?.name != null) Poster.Mustard else Poster.Ghost))
    }
}
