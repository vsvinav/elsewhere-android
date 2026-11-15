package com.elsewhere.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Pass

/** A stand-in section for shell tests: the tab's name on a pass, so no tab needs its injected ViewModel. */
@Composable
fun Placeholder(tab: Tab) {
    Box(Modifier.fillMaxSize().padding(20.dp)) { Pass { DisplayText(tab.label, 48.sp, misreg = true) } }
}
