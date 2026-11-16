package com.elsewhere.app.ui.crew

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.crew.FriendFix
import com.elsewhere.app.map.MapHost
import com.elsewhere.app.record.age
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.motionOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

const val FRIEND_STAMP_TAG = "friend-stamp"
const val SONAR_RING_TAG = "sonar-ring"
/** How long the rings spread after a hit. */
const val RING_MS = 1_200

/** One friend on the map: where their last ping put them and how old it is. */
data class FriendMark(val id: String, val name: String, val initial: String, val city: String?, val lat: Double, val lng: Double, val ageLabel: String, val stale: Boolean) {
    companion object {
        fun of(f: FriendFix, now: Long) = FriendMark(f.deviceId, f.name, f.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?", f.city, f.lat, f.lng, age(f.t, now / 1000), f.stale(now))
    }
}

/** The Friends layer's state as the map prints it. */
data class FriendsUi(
    val marks: List<FriendMark>,
    val layerOn: Boolean,
    val selected: String? = null,
    /** Friends whose Sonar hit is still ringing. */
    val hits: Set<String> = emptySet(),
    val myCity: String? = null,
    val crew: CrewUi? = null,
)

data class FriendActions(val layer: (Boolean) -> Unit = {}, val select: (String?) -> Unit = {}, val share: () -> Unit = {}, val stop: () -> Unit = {})

/** Every mark's screen position, re-projected per camera frame and when the host first has a projection. */
fun friendOffsets(host: MapHost, marks: List<FriendMark>): Flow<Map<String, Offset>> =
    merge(host.camera, host.viewport.filterNotNull()).map { marks.mapNotNull { m -> host.project(m.lat, m.lng)?.let { m.id to it } }.toMap() }

/**
 * Mustard stamps with an initial over each friend's last fix; grey once the fix is older than 15 min. Positions are
 * read in layout alone, so a camera frame moves the stamps without recomposing them.
 */
@Composable
fun FriendStamps(ui: FriendsUi, at: State<Map<String, Offset>>, onTap: (String) -> Unit, modifier: Modifier = Modifier) {
    Layout(
        content = { for (m in ui.marks) FriendStamp(m, m.id == ui.selected, m.id in ui.hits, onTap) },
        modifier = modifier,
    ) { measurables, c ->
        val placeables = measurables.map { it.measure(c.copy(minWidth = 0, minHeight = 0)) }
        layout(c.maxWidth, c.maxHeight) {
            placeables.forEachIndexed { i, p ->
                val o = at.value[ui.marks[i].id] ?: return@forEachIndexed
                p.place((o.x - p.width / 2f).toInt(), (o.y - p.height / 2f).toInt())
            }
        }
    }
}

@Composable
private fun FriendStamp(m: FriendMark, selected: Boolean, hit: Boolean, onTap: (String) -> Unit) {
    val fill = if (m.stale) Poster.Stock3 else Poster.Mustard
    val edge = if (m.stale) Poster.Ink3 else Poster.Ink
    Box(Modifier.size(RING_BOX), contentAlignment = Alignment.Center) {
        if (hit) SonarRings(Modifier.size(RING_BOX).testTag(SONAR_RING_TAG))
        Box(
            Modifier.size(STAMP).background(fill).border(if (selected) 3.dp else 2.dp, edge)
                .clickable(role = Role.Button) { onTap(m.id) }
                .testTag(FRIEND_STAMP_TAG).semantics { contentDescription = "${m.name} · ${m.city ?: "somewhere"} · ${m.ageLabel}" },
            contentAlignment = Alignment.Center,
        ) {
            DisplayText(m.initial, 22.sp, color = if (m.stale) Poster.Ink3 else Poster.Ink)
            if (!m.stale) Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(7.dp).background(Poster.Red))
        }
    }
}

private val STAMP = 40.dp
private val RING_BOX = 120.dp

/** Two red rings spreading out of the stamp over 1.2 s; a still pair under reduced motion. */
@Composable
fun SonarRings(modifier: Modifier = Modifier) {
    val motion = motionOn()
    val k = remember { Animatable(if (motion) 0f else 0.6f) }
    LaunchedEffect(motion) { if (motion) k.animateTo(1f, tween(RING_MS, easing = LinearOutSlowInEasing)) }
    Canvas(modifier) {
        val max = size.minDimension / 2
        val t = k.value
        for (lag in listOf(0f, 0.3f)) {
            val p = (t - lag).coerceIn(0f, 1f)
            if (p <= 0f) continue
            drawCircle(Poster.Red.copy(alpha = (1f - p) * 0.9f), max * (0.25f + 0.75f * p), center, style = Stroke(3.dp.toPx()))
        }
    }
}

/** "Marta · Lisbon · 3 min ago", and "You're both in Lisbon" when the cities agree. */
@Composable
fun FriendBubble(m: FriendMark, myCity: String?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val same = m.city != null && myCity != null && m.city.equals(myCity, ignoreCase = true)
    Row(modifier.fillMaxWidth().background(Poster.Stock).border(2.dp, Poster.Ink).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(72.dp)) {
            CapsText("Friends", size = 9.sp)
            DisplayText("Live", 30.sp, color = Poster.Red)
        }
        Column(Modifier.weight(1f)) {
            CapsText("${m.name} · ${m.city ?: "somewhere"} · ${m.ageLabel}", color = Poster.Ink2, size = 9.sp)
            if (same) { Spacer(Modifier.height(4.dp)); DisplayText("You're both in ${m.city}", 20.sp) }
            Spacer(Modifier.height(4.dp))
            Text(if (m.stale) "Last known · stale after 15 min" else "Their last fix · ${if (m.city != null) "city-level" else "precise"}", color = Poster.Ink3, style = PosterType.Label)
        }
        Text("×", Modifier.clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = "Close" }.padding(8.dp), color = Poster.Ink, style = PosterType.Display.copy(fontSize = 22.sp))
    }
}
