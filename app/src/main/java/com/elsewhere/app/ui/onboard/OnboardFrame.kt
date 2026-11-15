package com.elsewhere.app.ui.onboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Poster

/** The web's `.ob-bill`: the mark, the five-dash stepper, an optional Cancel, then the page. */
@Composable
fun OnboardFrame(question: Int?, onCancel: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Poster.Stock).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Poster.Ink), contentAlignment = Alignment.Center) { DisplayText("E", 26.sp, color = Poster.Stock) }
            if (question != null) {
                Spacer(Modifier.width(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in QUESTIONS.indices) {
                        val color = when { i == question -> Poster.Red; i < question -> Poster.Ink; else -> Poster.Stock3 }
                        Box(Modifier.width(28.dp).height(5.dp).background(color))
                    }
                }
                Spacer(Modifier.width(14.dp))
                CapsText("${question + 1} / ${QUESTIONS.size}", size = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            if (onCancel != null) CapsText("Cancel", Modifier.clickable(onClick = onCancel).padding(8.dp), color = Poster.Ink, size = 11.sp)
        }
        Spacer(Modifier.height(40.dp))
        content()
        Spacer(Modifier.height(48.dp))
    }
}
