package com.elsewhere.app.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** An ink block printed in stock, with a red rule on top: the dark stat panels. */
@Composable
fun Bill(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(Poster.Ink)) {
        Box(Modifier.fillMaxWidth().height(Rule).background(Poster.Red))
        CompositionLocalProvider(LocalContentColor provides Poster.Stock) {
            Column(Modifier.padding(24.dp)) {
                CapsText(title, color = Poster.Stock3)
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}
