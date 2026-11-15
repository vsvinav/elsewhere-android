package com.elsewhere.app.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
