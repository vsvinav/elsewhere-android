package com.elsewhere.app.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val Rule = 6.dp

/**
 * A ticket on the sheet: 6dp rule on top, hairline ink border around. With a `stub`, the torn-off
 * ink part prints beneath the body (the web puts it beside; a phone is too narrow), its fields wrapping.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Pass(modifier: Modifier = Modifier, stub: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(Poster.Stock).border(2.dp, Poster.Ink)) {
        Box(Modifier.fillMaxWidth().height(Rule).background(Poster.Ink))
        Column(Modifier.padding(20.dp), content = content)
        if (stub != null) {
            CompositionLocalProvider(LocalContentColor provides Poster.Stock, LocalMuted provides Poster.Stock3) {
                FlowRow(
                    Modifier.fillMaxWidth().background(Poster.Ink).padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
                    content = { stub() },
                )
            }
        }
    }
}
