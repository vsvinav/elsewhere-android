package com.elsewhere.app.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Bars as a deterministic hash of the text, like the web's; decoration, not a scannable Code 128. */
@Composable
fun Barcode(text: String, modifier: Modifier = Modifier) {
    val bars = remember(text) {
        var h = 2166136261L
        val out = ArrayList<Pair<Int, Int>>()
        var x = 0; var i = 0
        while (x < 300) {
            h = h xor text[i % text.length].code.toLong()
            h = (h * 16777619L) and 0xffffffffL
            val w = 1 + (h % 3).toInt()
            out.add(x to w); x += w + 1 + ((h shr 8) % 3).toInt(); i++
        }
        out
    }
    val color = LocalContentColor.current
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(48.dp)) {
            val u = size.width / 300f
            for ((x, w) in bars) drawRect(color, Offset(x * u, 0f), Size(w * u, size.height))
        }
        Spacer(Modifier.height(8.dp))
        CapsText(text, size = 11.sp)
    }
}
