package com.elsewhere.app.ui.shell

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

// Port of web/src/app/yearWheel.ts and world/YearWheel.tsx: the year as the control.

private fun yearOf(ts: Long): Int = dayOf(ts).year

/** Every calendar year (UTC, the record's day convention) the record touches, first to last. */
fun yearsOf(range: LongRange): List<Int> = (yearOf(range.first)..yearOf(range.last)).toList()

/** The whole-year window for `y`, cut to the record: what a year chip selects. */
fun yearSpan(range: LongRange, y: Int): LongRange =
    maxOf(range.first, LocalDate.of(y, 1, 1).epoch())..minOf(range.last, LocalDate.of(y + 1, 1, 1).epoch() - 1)

data class WheelState(val year: Int?, val from: Long, val to: Long)

/** The one year `selected` covers exactly, or `null` for the whole record or a custom window. */
fun wheelState(range: LongRange, selected: LongRange): WheelState {
    val from = maxOf(range.first, selected.first); val to = minOf(range.last, selected.last)
    val y = yearOf(from)
    val span = yearSpan(range, y)
    val year = if (from == span.first && to == span.last && yearOf(to) == y) y else null
    return WheelState(year, from, to)
}

private fun clampYear(range: LongRange, y: Int) = y.coerceIn(yearOf(range.first), yearOf(range.last))

/** The neighbouring whole year, clamped to the record; from "all", `+1` lands on the first year and `-1` on the last. */
fun stepYear(range: LongRange, selected: LongRange, delta: Int): LongRange {
    val year = wheelState(range, selected).year
        ?: return if (delta == 0) range else yearSpan(range, yearOf(if (delta > 0) range.first else range.last))
    return yearSpan(range, clampYear(range, year + delta))
}

/** The year under the finger after dragging `dxPx` from `startYear`; `null` starts from the last year. */
fun dragYear(range: LongRange, startYear: Int?, dxPx: Float, pxPerYear: Float = 120f): Int =
    clampYear(range, (startYear ?: yearOf(range.last)) + (dxPx / pxPerYear).roundToInt())

val PX_PER_YEAR = 120.dp
private const val FLAP_MS = 240

/**
 * Drag, scroll or arrow across a huge year and the poster re-prints under your finger. Every year
 * crossed calls `onRange` at once; release eases the row back onto it. Tapping the big year returns
 * to the whole record; ghosts and ticks jump straight to a year.
 */
@Composable
fun YearWheel(bounds: LongRange, selected: LongRange, onRange: (LongRange) -> Unit, modifier: Modifier = Modifier) {
    val years = remember(bounds) { yearsOf(bounds) }
    val year = wheelState(bounds, selected).year
    val pxPerYear = with(LocalDensity.current) { PX_PER_YEAR.toPx() }
    // The gesture detectors outlive any one range, so they read the latest values through these.
    val cur by rememberUpdatedState(year)
    val sel by rememberUpdatedState(selected)
    val emit by rememberUpdatedState(onRange)
    var dragging by remember { mutableStateOf(false) }
    var dx by remember { mutableFloatStateOf(0f) }
    var startYear by remember { mutableStateOf<Int?>(null) }
    var live by remember { mutableStateOf<Int?>(null) }
    var lastWheel by remember { mutableLongStateOf(0L) }

    val go = { y: Int? -> emit(if (y == null) bounds else yearSpan(bounds, y)) }
    val step = { d: Int -> emit(stepYear(bounds, sel, d)) }
    val last = years.last()
    val start = startYear ?: last
    val shift = if (dragging) dx + ((live ?: start) - start) * pxPerYear else 0f

    val gestures = Modifier
        .pointerInput(bounds) {
            detectHorizontalDragGestures(
                onDragStart = { dragging = true; dx = 0f; startYear = cur; live = cur },
                onDragEnd = { dragging = false },
                onDragCancel = { dragging = false },
            ) { change, delta ->
                change.consume()
                dx += delta
                // The row rides with the finger, so dragging left pulls later years into view.
                val y = dragYear(bounds, startYear, -dx, pxPerYear)
                if (y != live) { live = y; go(y) }
            }
        }
        .pointerInput(bounds) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    if (e.type != PointerEventType.Scroll) continue
                    val s = e.changes.first().scrollDelta
                    val d = if (abs(s.x) >= abs(s.y)) s.x else 0f
                    if (d == 0f) continue
                    // Trackpads fire many events per flick; only one step lands per flap.
                    val now = System.currentTimeMillis()
                    if (now - lastWheel < FLAP_MS) continue
                    lastWheel = now
                    e.changes.forEach { it.consume() }
                    step(if (d > 0) 1 else -1)
                }
            }
        }
        .onKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (e.key) {
                Key.DirectionRight, Key.DirectionUp -> step(1)
                Key.DirectionLeft, Key.DirectionDown -> step(-1)
                Key.MoveHome -> go(years.first())
                Key.MoveEnd -> go(last)
                Key.Escape -> go(null)
                else -> return@onKeyEvent false
            }
            true
        }
        .focusable()
        .semantics {
            contentDescription = "Year"
            stateDescription = year?.toString() ?: "All years"
            progressBarRangeInfo = ProgressBarRangeInfo((year ?: last).toFloat(), years.first().toFloat()..last.toFloat(), years.size - 1)
            setProgress { v -> go(v.roundToInt().coerceIn(years.first(), last)); true }
        }

    YearWheelFace(
        years, year, shiftPx = shift, dragging = dragging,
        onBig = { go(null) }, onYear = { y -> go(y) }, onAll = { go(null) },
        modifier = modifier, wheelModifier = gestures,
    )
}

/** Whether the flap and the snap play: off for snapshots and when the device has animations turned off. */
@Composable
private fun motionOn(): Boolean {
    val context = LocalContext.current
    val scale = remember(context) { runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) }
    return LocalMotion.current && scale != 0f
}

/** The wheel's printed face, with the row pushed `shiftPx` off centre: what a mid-drag frame looks like. */
@Composable
fun YearWheelFace(
    years: List<Int>, year: Int?, shiftPx: Float = 0f, dragging: Boolean = false,
    onBig: () -> Unit = {}, onYear: (Int) -> Unit = {}, onAll: () -> Unit = {},
    modifier: Modifier = Modifier, wheelModifier: Modifier = Modifier,
) {
    val motion = motionOn()
    val idx = if (year == null) -1 else years.indexOf(year)
    val prev = when { idx > 0 -> years[idx - 1]; idx < 0 -> years.last(); else -> null }
    val next = if (idx >= 0 && idx < years.lastIndex) years[idx + 1] else null
    // Mid-drag the row follows the finger exactly; on release the leftover eases back over one flap.
    val shift by animateFloatAsState(shiftPx, if (dragging || !motion) snap() else tween(FLAP_MS), label = "wheel")

    Column(modifier.fillMaxWidth()) {
        Row(
            wheelModifier.fillMaxWidth().clipToBounds().offset { IntOffset(shift.roundToInt(), 0) },
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom,
        ) {
            // Ghosts measure at full size and hang off the edges, so only their inner digits show.
            Box(Modifier.weight(1f).clipToBounds(), contentAlignment = Alignment.CenterEnd) {
                if (prev != null) Ghost(prev, Modifier.wrapContentWidth(Alignment.End, unbounded = true).testTag("wheel-prev").clickable(role = Role.Button, onClickLabel = "Year $prev", onClick = { onYear(prev) }))
            }
            FlapFigure(
                year?.toString() ?: "ALL", motion,
                Modifier.clickable(role = Role.Button, onClickLabel = if (year == null) "All years" else "Back to all years", onClick = onBig)
                    .semantics { contentDescription = if (year == null) "All years" else "Year $year" }.testTag("wheel-big"),
            )
            Box(Modifier.weight(1f).clipToBounds(), contentAlignment = Alignment.CenterStart) {
                if (next != null) Ghost(next, Modifier.wrapContentWidth(Alignment.Start, unbounded = true).testTag("wheel-next").clickable(role = Role.Button, onClickLabel = "Year $next", onClick = { onYear(next) }))
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(Poster.Ink))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (y in years) Tick(y.toString(), y == year) { onYear(y) }
            Tick("All", year == null, onAll)
        }
    }
}

@Composable
private fun Ghost(y: Int, modifier: Modifier) {
    Text("$y", modifier.alpha(0.55f), color = Poster.Ghost, style = BigStyle, maxLines = 1, softWrap = false)
}

private val BigStyle = PosterType.Display.copy(fontSize = 118.sp, lineHeight = 0.82.em, letterSpacing = (-0.02).em)

/** The figure one glyph at a time, each slot flipping over 240 ms when its digit changes. */
@Composable
private fun FlapFigure(text: String, motion: Boolean, modifier: Modifier) {
    Box(modifier) {
        Row(Modifier.offset(2.dp, 2.dp)) { for (c in text) Glyph(c, Poster.Red, motion) }
        Row { for (c in text) Glyph(c, Poster.Ink, motion) }
    }
}

@Composable
private fun Glyph(c: Char, color: Color, motion: Boolean) {
    if (!motion) { Text("$c", color = color, style = BigStyle, maxLines = 1, softWrap = false); return }
    AnimatedContent(
        c, label = "flap",
        transitionSpec = {
            (slideInVertically(tween(FLAP_MS)) { -it / 2 } + fadeIn(tween(FLAP_MS))) togetherWith (slideOutVertically(tween(FLAP_MS)) { it / 2 } + fadeOut(tween(FLAP_MS)))
        },
    ) { ch -> Text("$ch", color = color, style = BigStyle, maxLines = 1, softWrap = false) }
}

@Composable
private fun Tick(label: String, on: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.testTag("tick-$label").semantics { selected = on }.clickable(role = Role.Button, onClick = onClick).padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.width(2.dp).height(if (on) 12.dp else 7.dp).background(if (on) Poster.Red else Poster.Ink3))
        Spacer(Modifier.height(4.dp))
        Text(label.uppercase(), color = if (on) Poster.Red else Poster.Ink3, style = PosterType.Caps.copy(fontSize = 9.sp, letterSpacing = 0.12.em), textAlign = TextAlign.Center, maxLines = 1)
    }
}
