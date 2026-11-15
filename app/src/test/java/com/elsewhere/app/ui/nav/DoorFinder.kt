package com.elsewhere.app.ui.nav

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performSemanticsAction

/** Opens the first door labelled `label` the way a screen reader would: through its button's click action. */
fun SemanticsNodeInteractionsProvider.openDoor(label: String) {
    onAllNodesWithContentDescription("Open on the map: $label", useUnmergedTree = true)[0].performSemanticsAction(SemanticsActions.OnClick)
}
