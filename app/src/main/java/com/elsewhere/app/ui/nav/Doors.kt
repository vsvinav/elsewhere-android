package com.elsewhere.app.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
 * red, and lifting opens its door. Each door is also an accessibility action, "Open on the map: {label}".
 * `pressed` holds a run down, for previews.
 */
@Composable
fun DoorText(spans: List<Span>, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified, pressed: Int? = null) {
    val open = LocalOpenDoor.current
    var down by remember { mutableStateOf(pressed) }
    // Plain holder, not state: nothing in composition reads the layout, only taps do.
    val layout = remember { arrayOfNulls<TextLayoutResult>(1) }
    // Character offsets of each run, so a tap's offset finds its run by scanning a handful of spans.
    val starts = remember(spans) { IntArray(spans.size + 1).also { a -> for (i in spans.indices) a[i + 1] = a[i] + spans[i].text.length } }
    val text = remember(spans, down) {
        buildAnnotatedString {
            spans.forEachIndexed { i, s ->
                if (i == down && s.door != null) withStyle(SpanStyle(color = Poster.Red, textDecoration = TextDecoration.Underline)) { append(s.text) } else append(s.text)
            }
        }
    }
    fun runAt(pos: Offset): Int? {
        val l = layout[0] ?: return null
        if (pos.x < 0f || pos.y < 0f || pos.x > l.size.width || pos.y > l.size.height) return null
        val off = l.getOffsetForPosition(pos)
        val i = spans.indices.firstOrNull { off >= starts[it] && off < starts[it + 1] } ?: return null
        return i.takeIf { spans[it].door != null }
    }
    Text(
        text,
        modifier
            .semantics { customActions = spans.mapNotNull { s -> s.door?.let { d -> CustomAccessibilityAction("Open on the map: ${d.label}") { open(d); true } } } }
            .pointerInput(spans) {
                detectTapGestures(
                    onPress = { p -> val i = runAt(p); if (i != null) { down = i; tryAwaitRelease(); down = pressed } },
                    onTap = { p -> runAt(p)?.let { open(spans[it].door!!) } },
                )
            },
        color = color, style = style, onTextLayout = { layout[0] = it },
    )
}
