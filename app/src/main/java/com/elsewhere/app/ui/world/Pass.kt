package com.elsewhere.app.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val Rule = 6.dp

/** A ticket on the sheet: 6dp rule on top, hairline ink border around. */
@Composable
fun Pass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(Poster.Stock).border(2.dp, Poster.Ink)) {
        Box(Modifier.fillMaxWidth().height(Rule).background(Poster.Ink))
        Column(Modifier.padding(20.dp), content = content)
    }
}
