package com.elsewhere.app.ui.nav

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/**
 * Opens the door labelled `label`, whether it is a [Door] of its own (a clickable with the description) or a
 * figure inside a [DoorText] (an accessibility action on the paragraph). Fails when no such door is shown.
 */
fun SemanticsNodeInteractionsProvider.openDoor(label: String) {
    val want = "Open on the map: $label"
    val described = onAllNodesWithContentDescription(want, useUnmergedTree = true)
    if (described.fetchSemanticsNodes().isNotEmpty()) {
        val node = described[0]
        runCatching { node.performScrollTo() }
        node.performClick(); return
    }
    val nodes = onAllNodes(!isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
    for (n in nodes) {
        val a = n.config.getOrNull(SemanticsActions.CustomActions)?.firstOrNull { it.label == want } ?: continue
        a.action(); return
    }
    throw AssertionError("No door \"$label\" on screen; doors shown: " + nodes.flatMap { n ->
        n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() + n.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
    }.filter { it.startsWith("Open on the map") })
}
