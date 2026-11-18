package com.elsewhere.app.ui.tabs.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.pow
import kotlin.math.roundToInt

// Port of the strip in web/src/map/Hud.tsx: time under the finger, finer the further it rises off the strip.

const val SCRUB_STEP_S = 600.0
/** How long the rail stays after the finger lifts. */
const val SCRUB_LINGER_MS = 600L
private const val PINCH_IN = 0.8f
private const val PINCH_OUT = 1.25f

/** Where each New Year falls along the scrub, as a fraction of the window. */
fun yearTicks(range: LongRange): List<Pair<Int, Float>> {
    val m0 = range.first; val m1 = range.last
    if (m1 <= m0) return emptyList()
    val y0 = Instant.ofEpochSecond(m0).atOffset(ZoneOffset.UTC).year; val y1 = Instant.ofEpochSecond(m1).atOffset(ZoneOffset.UTC).year
    return (y0 + 1..y1).map { y -> y to ((java.time.LocalDate.of(y, 1, 1).atStartOfDay(ZoneOffset.UTC).toEpochSecond() - m0).toFloat() / (m1 - m0)) }
}

/** Full scale on the strip; each strip height above it divides the scale by 4.5, down to a twentieth. */
fun fineness(y: Float, height: Float): Double = if (y >= 0f) 1.0 else maxOf(1 / 20.0, 1 / 4.5.pow((-y / height).toDouble()))

fun scrubDelta(from: Double, dx: Float, y: Float, width: Float, height: Float, range: LongRange): Double {
    val perPx = (range.last - range.first).toDouble() / maxOf(1f, width)
    return (from + dx * perPx * fineness(y, height)).coerceIn(range.first.toDouble(), range.last.toDouble())
}

/**
 * The timeline: a strip you drag to hold time, with each New Year marked on it, and a slider under it for keys and
 * TalkBack. A pinch on the strip steps the grain once per gesture and the drag is dropped for the rest of it.
 */
@Composable
fun Scrub(range: LongRange, playhead: Double, onScrub: (Double) -> Unit, onScrubbing: (Boolean) -> Unit, onGrainStep: (Int) -> Unit) {
    val span = (range.last - range.first).toDouble().coerceAtLeast(1.0)
    val frac = (((if (playhead.isNaN()) range.first.toDouble() else playhead) - range.first) / span).toFloat().coerceIn(0f, 1f)
    val years = remember(range) { yearTicks(range) }
    val measurer = rememberTextMeasurer()
    val style = PosterType.Label.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Poster.Ink3)
    val haptic = LocalHapticFeedback.current
    // The time of the last lift; the rail lingers a beat after it before the strip lets go.
    var lifted by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lifted) { if (lifted != 0L) { delay(SCRUB_LINGER_MS); onScrubbing(false) } }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(56.dp).background(Poster.Stock2).testTag("scrub")
                .pointerInput(range) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val w = size.width.toFloat(); val h = size.height.toFloat()
                        lifted = 0L; onScrubbing(true)
                        var t = scrubDelta(range.first.toDouble(), down.position.x, 0f, w, h, range)
                        var lastX = down.position.x
                        onScrub(t)
                        var pinch = 0f; var pinched = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                val d = (pressed[0].position - pressed[1].position).getDistance()
                                if (pinch == 0f) pinch = d
                                else if (!pinched && pinch > 0f) {
                                    val s = d / pinch
                                    if (s < PINCH_IN || s > PINCH_OUT) {
                                        pinched = true; onGrainStep(if (s < PINCH_IN) -1 else 1)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            } else if (pinch == 0f) {
                                val p = pressed[0]
                                t = scrubDelta(t, p.position.x - lastX, p.position.y.coerceAtMost(0f), w, h, range)
                                lastX = p.position.x; onScrub(t)
                            }
                            ev.changes.forEach { it.consume() }
                        }
                        lifted = System.nanoTime()
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                for ((y, pct) in years) {
                    val x = pct * size.width
                    val t = measurer.measure(y.toString(), style)
                    drawText(t, topLeft = Offset((x - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), 2.dp.toPx()))
                    drawLine(Poster.Ghost, Offset(x, t.size.height + 2.dp.toPx()), Offset(x, size.height), 1.dp.toPx())
                }
            }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(frac).height(3.dp).background(Poster.Red))
        }
        PosterSlider(
            frac, { f -> onScrub(range.first + ((f * span) / SCRUB_STEP_S).roundToInt() * SCRUB_STEP_S) }, 0f..1f,
            Modifier.fillMaxWidth().semantics { contentDescription = "Timeline position" },
        )
    }
}
