package com.elsewhere.app.ui.tabs.facts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.app.analytics.Page
import com.elsewhere.app.analytics.Passport
import com.elsewhere.app.analytics.Stamp
import com.elsewhere.app.analytics.tilt
import com.elsewhere.app.ui.nav.door
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Field
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Lede
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.Share
import com.elsewhere.app.ui.world.date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Port of web/src/tabs/Facts/Passport.tsx: a booklet of country and milestone pages, each earned stamp a door.

internal const val THUD_MS = 240

@Composable
fun PassportTab(vm: PassportViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    ui?.let { PassportBooklet(it.passport, it.seen, onSeen = vm::seen) }
}

/** The stamp's current press-in scale, so a test can watch the thud land. */
val StampScale = SemanticsPropertyKey<Float>("StampScale")

/**
 * Stamps earned since the booklet was last open press in once, then are recorded; the first ever open just records
 * what is there. `seen` is read when the earned set changes, so the save it triggers does not re-judge the thud.
 */
@Composable
private fun rememberFresh(pass: Passport, seen: Set<String>?, onSeen: (Set<String>) -> Unit): Set<String> {
    val earned = pass.earnedIds
    val latestSeen by rememberUpdatedState(seen)
    val save by rememberUpdatedState(onSeen)
    var fresh by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(earned) {
        val was = latestSeen
        val now = if (was == null) emptyList() else earned.filter { it !in was }
        if (was == null || now.isNotEmpty()) save(earned.toSet())
        // The press-in plays once: after it has landed the stamp is no longer fresh, however often its page comes back.
        if (now.isNotEmpty()) { fresh = now.toSet(); delay(THUD_MS + 60L); fresh = emptySet() }
    }
    return fresh
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PassportBooklet(pass: Passport, seen: Set<String>?, modifier: Modifier = Modifier, onSeen: (Set<String>) -> Unit = {}) {
    val fresh = rememberFresh(pass, seen, onSeen)
    val n = pass.pages.size
    val pager = rememberPagerState(pageCount = { n })
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth().background(Poster.Stock).padding(horizontal = 12.dp, vertical = 24.dp)) {
        Column {
            DisplayText("Pass", 72.sp, misreg = true)
            DisplayText("port", 72.sp, color = Poster.Red, misreg = true)
        }
        Spacer(Modifier.height(18.dp))
        Lede("Pages per country and per milestone. Stamps you have earned carry their date; the outlines are the ones to aim for.")
        Spacer(Modifier.height(18.dp))
        Field("Stamped", "${pass.earnedIds.size} / ${pass.total}")
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            InkButton("←", { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, Modifier.semantics { contentDescription = "Previous page" }, quiet = true, enabled = pager.currentPage > 0)
            CapsText("Page ${pager.currentPage + 1} of $n", color = Poster.Ink3, size = 11.sp)
            InkButton("→", { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, Modifier.semantics { contentDescription = "Next page" }, quiet = true, enabled = pager.currentPage < n - 1)
        }
        Spacer(Modifier.height(18.dp))
        HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 16.dp, verticalAlignment = Alignment.Top) { i ->
            PassportPage(pass.pages[i], i, n, fresh)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PassportPage(page: Page, index: Int, total: Int, fresh: Set<String> = emptySet(), modifier: Modifier = Modifier) {
    val text = { page.title + "\n" + page.stamps.filter { it.earned }.joinToString("\n") { "${it.label} · ${date(it.earnedAt!!)}" } }
    Pass(modifier.semantics { contentDescription = "${page.title}, page ${index + 1} of $total" }, share = Share(page.title, text)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(page.title, color = Poster.Ink, style = PosterType.Display.copy(fontSize = 30.sp, lineHeight = 0.9.em, letterSpacing = 0.02.em))
            CapsText("${page.earned} / ${page.stamps.size} earned", color = Poster.Ink3, size = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Poster.Ink))
        Spacer(Modifier.height(22.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(18.dp), maxItemsInEachRow = 3) {
            for (s in page.stamps) PassportStamp(s, fresh = s.id in fresh)
        }
        Spacer(Modifier.height(18.dp))
        CapsText("${"%02d".format(index + 1)} · ${"%02d".format(total)}", Modifier.align(Alignment.End), color = Poster.Ink3, size = 10.sp)
    }
}

/** `10 NIGHTS AWAY` prints as a big 10 over small words; a city prints as its name. */
private val LEAD = Regex("^([\\d,]+)\\s+(.+)$")
private fun split(label: String): Pair<String?, String> = LEAD.matchEntire(label)?.let { it.groupValues[2] to it.groupValues[1] } ?: (null to label)

/**
 * One round stamp, leaning by its hash. Earned: red ink, the nights or the words, the figure, the date, a door to what
 * made it. Unearned: a dashed ghost outline that opens nothing. `fresh` presses the stamp in once, 1.3 → 1 over 240 ms.
 */
@Composable
fun PassportStamp(s: Stamp, modifier: Modifier = Modifier, fresh: Boolean = false, diameter: Dp = 104.dp, animate: Boolean = LocalMotion.current) {
    val (top, big) = split(s.label)
    val earned = s.earned && s.door != null
    val color = if (earned) Poster.Red else Poster.Ghost
    val thud = fresh && animate
    val scale = remember(s.id) { Animatable(1f) }
    LaunchedEffect(s.id, thud) { if (thud) { scale.snapTo(1.3f); scale.animateTo(1f, tween(THUD_MS)) } else scale.snapTo(1f) }
    val lean = tilt(s.id)
    val dash = if (earned) null else PathEffect.dashPathEffect(floatArrayOf(10f, 7f))
    val sem = if (earned) Modifier.door(s.door) else Modifier.semantics { role = Role.Button; contentDescription = "${s.label}: not yet"; disabled() }
    Box(
        modifier.size(diameter).then(sem).semantics { this[StampScale] = scale.value }
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = lean }
            .drawBehind {
                val outer = 3.dp.toPx(); val inner = 1.5.dp.toPx(); val gap = 4.dp.toPx()
                val r = this.size.minDimension / 2
                drawCircle(color, r - outer / 2, style = Stroke(outer, pathEffect = dash))
                drawCircle(color, r - outer - gap - inner / 2, style = Stroke(inner, pathEffect = dash))
            }
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val small = PosterType.Caps.copy(fontSize = 7.sp, letterSpacing = 0.14.em, lineHeight = 1.15.em)
            val line1 = if (earned) (top ?: s.sub ?: "") else (top ?: "")
            if (line1.isNotEmpty()) Text(line1.uppercase(), color = color, style = small, textAlign = TextAlign.Center, maxLines = 2)
            Text(big, Modifier.padding(vertical = 2.dp), color = color, style = PosterType.Display.copy(fontSize = 20.sp, lineHeight = 0.9.em), textAlign = TextAlign.Center, maxLines = 2)
            Text(if (earned) date(s.earnedAt!!) else "NOT YET", color = color, style = small, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}
