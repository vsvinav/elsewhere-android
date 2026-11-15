package com.elsewhere.app.ui.shell

import android.provider.Settings
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
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
import androidx.compose.ui.input.pointer.PointerInputChange
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

/** `year` is the one year `selected` covers exactly; `all` whether it is the whole record. Neither makes it a custom window. */
data class WheelState(val year: Int?, val all: Boolean, val from: Long, val to: Long)

fun wheelState(range: LongRange, selected: LongRange): WheelState {
    val from = maxOf(range.first, selected.first); val to = minOf(range.last, selected.last)
    val y = yearOf(from)
    val span = yearSpan(range, y)
    val year = if (from == span.first && to == span.last && yearOf(to) == y) y else null
    return WheelState(year, from == range.first && to == range.last, from, to)
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

/** Fallback pitch until the figure has been measured. */
val PX_PER_YEAR = 120.dp
private val GAP = 18.dp
private const val FLAP_MS = 240

/**
 * Drag, scroll or arrow across a huge year and the poster re-prints under your finger. Every year
 * crossed calls `onRange` at once; release eases the row back onto it. Tapping the big year returns
 * to the whole record; ghosts and ticks jump straight to a year.
 */
@Composable
fun YearWheel(bounds: LongRange, selected: LongRange, onRange: (LongRange) -> Unit, modifier: Modifier = Modifier, pitch: Dp? = null) {
    val years = remember(bounds) { yearsOf(bounds) }
    val state = wheelState(bounds, selected)
    val year = state.year
    val density = LocalDensity.current
    // The real pitch is the big figure plus the row gap; it changes with the digits on show.
    var bigWidth by remember { mutableIntStateOf(0) }
    val pxPerYear = with(density) { pitch?.toPx() ?: if (bigWidth > 0) bigWidth + GAP.toPx() else PX_PER_YEAR.toPx() }
    // The gesture detectors outlive any one range, so they read the latest values through these.
    // What the face prints, and where a drag starts from: the year, a custom window's first year, or null for ALL.
    val base by rememberUpdatedState(year ?: if (state.all) null else yearOf(state.from))
    val sel by rememberUpdatedState(selected)
    val emit by rememberUpdatedState(onRange)
    val pxNow by rememberUpdatedState(pxPerYear)
    var dragging by remember { mutableStateOf(false) }
    var dx by remember { mutableFloatStateOf(0f) }
    var startYear by remember { mutableStateOf<Int?>(null) }
    var live by remember { mutableStateOf<Int?>(null) }
    var wheelAcc by remember { mutableFloatStateOf(0f) }
    var wheelIdle by remember { mutableLongStateOf(0L) }

    val go = { y: Int? -> emit(if (y == null) bounds else yearSpan(bounds, y)) }
    val step = { d: Int -> emit(stepYear(bounds, sel, d)) }
    val last = years.last()
    val start = startYear ?: last
    val shift = if (dragging) dx + ((live ?: start) - start) * pxPerYear else 0f

    val gestures = Modifier
        .pointerInput(bounds) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val first = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
                // Displacement is measured from the finger's landing point, slop included, so the row never lags it.
                val x0 = down.position.x
                dragging = true; dx = first.position.x - x0; startYear = base; live = base ?: last
                val move = { change: PointerInputChange ->
                    change.consume()
                    dx = change.position.x - x0
                    // The row rides with the finger, so dragging left pulls later years into view.
                    val y = dragYear(bounds, startYear, -dx, pxNow)
                    if (y != live) { live = y; go(y) }
                }
                move(first)
                horizontalDrag(first.id, move)
                dragging = false
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
                    e.changes.forEach { it.consume() }
                    // A flick is many small events: one step per pitch of travel, forgotten after a pause.
                    val now = System.currentTimeMillis()
                    if (now - wheelIdle > WHEEL_IDLE_MS) wheelAcc = 0f
                    wheelIdle = now
                    wheelAcc += d
                    if (abs(wheelAcc) < pxNow) continue
                    step(if (wheelAcc > 0) 1 else -1)
                    wheelAcc -= Math.signum(wheelAcc) * pxNow
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

    YearWheelFace(
        years, state, shiftPx = shift, dragging = dragging,
        onBig = { if (!state.all) go(null) }, onYear = { y -> go(y) }, onAll = { go(null) },
        onBigSize = { bigWidth = it },
        modifier = modifier, wheelModifier = gestures,
        bigModifier = Modifier.semantics {
            contentDescription = "Year"
            stateDescription = year?.toString() ?: if (state.all) "All years" else "Custom window"
            progressBarRangeInfo = ProgressBarRangeInfo((year ?: last).toFloat(), years.first().toFloat()..last.toFloat(), years.size - 1)
            setProgress { v -> go(v.roundToInt().coerceIn(years.first(), last)); true }
        },
    )
}

private const val WHEEL_IDLE_MS = 300L

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
    years: List<Int>, state: WheelState, shiftPx: Float = 0f, dragging: Boolean = false,
    onBig: () -> Unit = {}, onYear: (Int) -> Unit = {}, onAll: () -> Unit = {}, onBigSize: (Int) -> Unit = {},
    modifier: Modifier = Modifier, wheelModifier: Modifier = Modifier, bigModifier: Modifier = Modifier,
) {
    val motion = motionOn()
    val year = state.year
    val custom = year == null && !state.all
    // A custom window prints its first year, muted, so the wheel still says where you are.
    val shown = year ?: if (custom) yearOf(state.from) else null
    val idx = if (shown == null) -1 else years.indexOf(shown)
    val prev = when { idx > 0 -> years[idx - 1]; idx < 0 -> years.last(); else -> null }
    val next = if (idx >= 0 && idx < years.lastIndex) years[idx + 1] else null
    // Mid-drag the row follows the finger exactly; on release the leftover eases back over one flap.
    val shift by animateFloatAsState(shiftPx, if (dragging || !motion) snap() else tween(FLAP_MS), label = "wheel")

    Column(modifier.fillMaxWidth()) {
        Row(
            wheelModifier.testTag("wheel-row").fillMaxWidth().clipToBounds().offset { IntOffset(shift.roundToInt(), 0) },
            horizontalArrangement = Arrangement.spacedBy(GAP), verticalAlignment = Alignment.Bottom,
        ) {
            // Ghosts measure at full size and hang off the edges, so only their inner digits show.
            Box(Modifier.weight(1f).clipToBounds(), contentAlignment = Alignment.CenterEnd) {
                if (prev != null) Ghost(prev, Modifier.wrapContentWidth(Alignment.End, unbounded = true).testTag("wheel-prev").clickable(role = Role.Button, onClickLabel = "Year $prev", onClick = { onYear(prev) }))
            }
            Box(
                bigModifier.testTag("wheel-big").onSizeChanged { onBigSize(it.width) }
                    .clickable(onClickLabel = if (state.all) null else "Back to all years", onClick = onBig),
            ) {
                FlapFigure(shown?.toString() ?: "ALL", motion, if (custom) Poster.Ink3 else Poster.Ink, misreg = !custom)
                if (custom) Text(
                    "CUSTOM", Modifier.align(Alignment.TopEnd).offset(6.dp, (-4).dp).rotate(-6f).background(Poster.Red).padding(horizontal = 6.dp, vertical = 3.dp),
                    color = Poster.Stock, style = PosterType.Caps.copy(fontSize = 10.sp, letterSpacing = 0.2.em),
                )
            }
            Box(Modifier.weight(1f).clipToBounds(), contentAlignment = Alignment.CenterStart) {
                if (next != null) Ghost(next, Modifier.wrapContentWidth(Alignment.Start, unbounded = true).testTag("wheel-next").clickable(role = Role.Button, onClickLabel = "Year $next", onClick = { onYear(next) }))
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(Poster.Ink))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (y in years) Tick(y.toString(), y == year) { onYear(y) }
            Tick("ALL", state.all, onAll)
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
private fun FlapFigure(text: String, motion: Boolean, color: Color, misreg: Boolean) {
    Box {
        if (misreg) Row(Modifier.offset(2.dp, 2.dp)) { for (c in text) Glyph(c, Poster.Red, motion) }
        Row { for (c in text) Glyph(c, color, motion) }
    }
}

/** Split-flap: the old glyph folds away to -90° over the first half, the new one unfolds from 90° over the second. */
@Composable
private fun Glyph(c: Char, color: Color, motion: Boolean) {
    var shown by remember { mutableStateOf(c) }
    val angle = remember { Animatable(0f) }
    LaunchedEffect(c, motion) {
        if (shown == c) return@LaunchedEffect
        if (!motion) { shown = c; return@LaunchedEffect }
        angle.animateTo(-90f, tween(FLAP_MS / 2, easing = FastOutLinearInEasing))
        shown = c
        angle.snapTo(90f)
        angle.animateTo(0f, tween(FLAP_MS / 2, easing = LinearOutSlowInEasing))
    }
    Text(
        "$shown", Modifier.graphicsLayer { rotationX = angle.value; cameraDistance = 12f * density },
        color = color, style = BigStyle, maxLines = 1, softWrap = false,
    )
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
