package com.elsewhere.app.ui.onboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.data.Units
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.NamingStamp
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.PosterTextField

/** One of the five questions, chosen by `state.step`; copy from Onboarding.tsx. */
@Composable
fun QuestionsScreen(state: OnboardState, actions: OnboardActions, onCancel: (() -> Unit)?) {
    val i = state.questionIndex
    OnboardFrame(question = i, onCancel = onCancel) {
        when (state.step) {
            Step.Name -> Question(
                "Whose record is this?", "It prints on the poster.",
                onNext = actions::next, skip = actions::next,
            ) {
                PosterTextField(state.name, actions::setName, Modifier.fillMaxWidth(), placeholder = "First name", display = true)
            }

            Step.Home -> Question(
                "Where is home?", "Ranked by where your phone sleeps. Trips, nights away and distance all start here.",
                onBack = actions::back, onNext = if (state.homes.isNotEmpty()) actions::next else null,
            ) {
                Cards {
                    for (c in state.sleep) {
                        val on = state.homes.size == 1 && c.matches(state.homes[0])
                        val city = if (c.city != null && c.label != c.city) "${c.city} · " else ""
                        val merged = if (c.members > 1) " · ${c.members} labels merged" else ""
                        CandidateCard(c, c.sure.label, "$city${nights(c.score)} · ${pct(c.share)}$merged", on, { actions.pickHome(c) }, sticker = c.sure != Sure.STAYED)
                    }
                    if (state.sleep.isEmpty()) Text("No overnight stays found in this record.", color = Poster.Ink2, style = PosterType.Lede)
                }
                NamingStamp(state.naming, Modifier.align(Alignment.End))
                if (state.homes.isNotEmpty()) {
                    Spacer(Modifier.height(30.dp))
                    Homes(state, actions, "Since when, and until when? Leave both blank if you lived here for the whole record.")
                }
            }

            Step.Moves -> Question(
                "Did you live anywhere else?",
                if (state.moves.isNotEmpty()) "Your nights shift in ${state.moves.joinToString(" and ") { it.month }}. Tap a place to add it as a home, then set the month each one started. Give a home an until and it can be added again for a later stint."
                else "Add another home and the month it started. Skip if you stayed put.",
                onBack = actions::back, onNext = actions::next, skip = actions::next,
            ) {
                Cards {
                    for (c in state.sleep) {
                        // A place already used as a home can come back as a later stint once its earlier one has an "until".
                        val last = state.homes.filter { c.matches(it) }.maxByOrNull { it.from }
                        if (last != null && last.to == null) continue
                        val mv = state.moves.firstOrNull { it.to == c.placeId }
                        val tag = when { last != null -> "Moved back"; mv != null -> "From ${mv.month}"; else -> c.cc ?: "—" }
                        val sub = if (last != null) "again, after ${fmtMonth(monthOf(last.to!!))}" else "${nights(c.score)} · ${pct(c.share)}"
                        CandidateCard(c, tag, sub, selected = false, onClick = { actions.addHome(c) })
                    }
                }
                Spacer(Modifier.height(30.dp))
                Homes(state, actions, null)
            }

            Step.Work -> Question(
                "Where do you spend weekdays?", "Optional. Pick every job on the record and set when each one ran. Skip it and nothing about work is printed.",
                onBack = actions::back, onNext = actions::next, skip = actions::skip,
            ) {
                Cards {
                    for (c in state.weekday) {
                        val on = state.works.any { it.placeId == c.placeId }
                        CandidateCard(c, c.cc ?: "—", "${Math.round(c.score)} weekday hours", on, { actions.toggleWork(c) })
                    }
                    if (state.weekday.isEmpty()) Text("No regular weekday place stands out.", color = Poster.Ink2, style = PosterType.Lede)
                }
                if (state.works.isNotEmpty()) {
                    Spacer(Modifier.height(30.dp))
                    StintList(
                        state.works.map { Stint(it.name ?: "Work", null, it.from, it.to) }, "work", state.range, removable = true, hint = "Blank dates mean the whole record.",
                        onFrom = actions::setWorkFrom, onTo = actions::setWorkTo, onRemove = actions::removeWork,
                    )
                }
            }

            Step.Units -> Question("Kilometres or miles?", null, onBack = actions::back, onNext = actions::finish, nextLabel = "Print my record") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UnitChip("Kilometres", state.units == Units.KM, Modifier.weight(1f)) { actions.setUnits(Units.KM) }
                    UnitChip("Miles", state.units == Units.MI, Modifier.weight(1f)) { actions.setUnits(Units.MI) }
                }
            }

            else -> Unit
        }
    }
}

@Composable
private fun Homes(state: OnboardState, actions: OnboardActions, hint: String?) {
    StintList(
        state.homes.map { Stint(it.name ?: it.city.ifEmpty { "Home" }, if (it.name != null && it.city.isNotEmpty() && it.name != it.city) it.city else null, it.from, it.to) },
        "home", state.range, removable = state.homes.size > 1, hint = hint,
        onFrom = actions::setHomeFrom, onTo = actions::setHomeTo, onRemove = actions::removeHome,
    )
}

@Composable
private fun Cards(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
private fun UnitChip(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(72.dp).background(if (on) Poster.Ink else Poster.Stock).border(2.dp, Poster.Ink).semantics { selected = on }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { DisplayText(label, 24.sp, color = if (on) Poster.Stock else Poster.Ink) }
}

/** The web's `Q`: headline, lede, the answer, then Next · Skip · Back. */
@Composable
private fun Question(
    title: String, sub: String?, onNext: (() -> Unit)?, onBack: (() -> Unit)? = null, skip: (() -> Unit)? = null,
    nextLabel: String = "Next", content: @Composable () -> Unit,
) {
    DisplayText(title, 56.sp)
    if (sub != null) { Spacer(Modifier.height(24.dp)); Text(sub, color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 17.sp)) }
    Spacer(Modifier.height(32.dp))
    content()
    Spacer(Modifier.height(40.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        InkButton(nextLabel, onNext ?: {}, enabled = onNext != null)
        if (skip != null) InkButton("Skip", skip, quiet = true)
        Spacer(Modifier.weight(1f))
        if (onBack != null) InkButton("Back", onBack, quiet = true)
    }
}
