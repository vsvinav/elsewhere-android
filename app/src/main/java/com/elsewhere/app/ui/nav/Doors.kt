package com.elsewhere.app.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.elsewhere.app.ui.world.Poster

/** Opens a door; the shell provides the real thing, snapshots and share cards get a no-op. */
val LocalOpenDoor = staticCompositionLocalOf<(Door) -> Unit> { {} }

/**
 * A printed figure that leads somewhere: a tap opens `target` on the map. Nothing is drawn until the
 * finger is down, when a red rule prints under the figure; a null target leaves the content as it was.
 */
@Composable
fun Door(target: Door?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (target == null) content() else Box(modifier.door(target)) { content() }
}

/** The door as a modifier, for rows and cells that cannot take a wrapper without changing their layout. */
@Composable
fun Modifier.door(target: Door?): Modifier {
    if (target == null) return this
    val open = LocalOpenDoor.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return this
        .clickable(interactionSource = source, indication = null, role = Role.Button) { open(target) }
        .semantics { contentDescription = "Open on the map: ${target.label}" }
        .drawBehind { if (pressed) drawLine(Poster.Red, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) }
}

/** A run of a [DoorText]: a figure with the door it opens, or the plain words between figures. */
data class Span(val text: String, val door: Door? = null)

/** Shorthand for the figures and words of a [DoorText]. */
fun span(text: String, door: Door? = null) = Span(text, door)

/**
 * One paragraph with doors in it: the figures read as the words around them, a finger on one underlines it in
 * red, and lifting opens its door. Taps are judged against the characters themselves, line by line, so blank
 * paper beside a line and the words between figures open nothing. For a screen reader each figure is its own
 * button, "Open on the map: {label}", framed where it prints. `pressed` holds a run down, for previews.
 */
@Composable
fun DoorText(spans: List<Span>, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified, pressed: Int? = null) {
    val open by rememberUpdatedState(LocalOpenDoor.current)
    // Plain holder, not state: nothing in composition reads the layout, only hit-tests and the buttons' measure do.
    val layout = remember { arrayOfNulls<TextLayoutResult>(1) }
    // Character offsets of each run, so a tap's character and a figure's frame map back to a run.
    val starts = remember(spans) { IntArray(spans.size + 1).also { a -> for (i in spans.indices) a[i + 1] = a[i] + spans[i].text.length } }
    val doors = remember(spans) { spans.indices.filter { spans[it].door != null } }
    var held by remember { mutableStateOf<Int?>(null) }
    val down = pressed ?: held
    val text = remember(spans, down) {
        buildAnnotatedString {
            spans.forEachIndexed { i, s ->
                if (i == down && s.door != null) withStyle(SpanStyle(color = Poster.Red, textDecoration = TextDecoration.Underline)) { append(s.text) } else append(s.text)
            }
        }
    }
    fun doorAt(pos: Offset): Int? {
        val l = layout[0] ?: return null
        val line = l.getLineForVerticalPosition(pos.y)
        if (pos.x < l.getLineLeft(line) || pos.x > l.getLineRight(line)) return null
        val i = runAt(starts, l.getOffsetForPosition(pos))
        return i.takeIf { it in spans.indices && spans[it].door != null }
    }
    Layout(
        content = {
            Text(
                text, color = color, style = style, onTextLayout = { layout[0] = it },
                modifier = Modifier.pointerInput(spans) {
                    detectTapGestures(
                        onPress = { pos -> doorAt(pos)?.let { i -> held = i; try { tryAwaitRelease() } finally { held = null } } },
                        onTap = { pos -> doorAt(pos)?.let { open(spans[it].door!!) } },
                    )
                },
            )
            doors.forEach { i ->
                val d = spans[i].door!!
                Box(Modifier.semantics { role = Role.Button; contentDescription = "Open on the map: ${d.label}"; onClick { open(d); true } })
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val paragraph = measurables[0].measure(constraints)
        val l = layout[0]
        val rects = doors.map { i -> if (l != null && starts[i + 1] > starts[i]) l.boundsOf(starts[i], starts[i + 1]) else Rect.Zero }
        val buttons = rects.mapIndexed { k, r -> measurables[k + 1].measure(Constraints.fixed(r.width.roundToInt().coerceAtLeast(0), r.height.roundToInt().coerceAtLeast(0))) }
        layout(paragraph.width, paragraph.height) {
            paragraph.place(0, 0)
            buttons.forEachIndexed { k, b -> b.place(rects[k].left.roundToInt(), rects[k].top.roundToInt()) }
        }
    }
}

/** Index of the run holding character `offset`: the last start at or before it. O(log runs). */
private fun runAt(starts: IntArray, offset: Int): Int {
    var lo = 0; var hi = starts.size - 2
    while (lo < hi) { val mid = (lo + hi + 1) ushr 1; if (starts[mid] <= offset) lo = mid else hi = mid - 1 }
    return lo
}

/** The box around characters [start, end): one line's slice, or the full lines a wrapped run spans. */
private fun TextLayoutResult.boundsOf(start: Int, end: Int): Rect {
    val last = (end - 1).coerceAtMost(layoutInput.text.length - 1)
    val first = getLineForOffset(start)
    val lastLine = getLineForOffset(last)
    val a = getHorizontalPosition(start, usePrimaryDirection = true)
    val b = getBoundingBox(last).right
    return if (first == lastLine) Rect(minOf(a, b), getLineTop(first), maxOf(a, b), getLineBottom(first))
    else Rect(minOf(a, getLineLeft(lastLine)), getLineTop(first), maxOf(getLineRight(first), b), getLineBottom(lastLine))
}
