package com.elsewhere.app.ui.world

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.share.CardRenderer
import com.elsewhere.app.share.LocalPrinting
import com.elsewhere.app.share.ShareFormat
import com.elsewhere.app.share.ShareSheet
import com.elsewhere.app.share.cardFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Port of the web's SaveMenu (web/src/world/index.tsx): a quiet "Save" on every bill and pass.

/** What a bill or pass offers to share: the name its file takes and, where there is one, a text version. */
class Share(val name: String, val text: (() -> String)? = null)

/**
 * "SAVE ▾" in the corner of a card, opening Free / Story / Post (and Text when the card has one).
 * `card` is the card as it should print, without this menu; it is composed off-screen and shared as a PNG.
 */
@Composable
fun SaveMenu(share: Share, card: @Composable () -> Unit, modifier: Modifier = Modifier, color: Color = LocalMuted.current) {
    if (LocalPrinting.current) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val printed by rememberUpdatedState(card)
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun save(format: ShareFormat) {
        val activity = context.findActivity() ?: return
        open = false; busy = true
        scope.launch {
            try {
                val bitmap = CardRenderer.render(activity, format, printed)
                val uri = withContext(Dispatchers.IO) { ShareSheet.writeCard(activity, bitmap, cardFileName(share.name, format)) }
                ShareSheet.shareImage(activity, uri)
            } finally { busy = false }
        }
    }

    Box(modifier) {
        CapsText(
            if (busy) "Saving…" else "Save ▾",
            Modifier.clickable(enabled = !busy, role = Role.Button) { open = true }.padding(8.dp).semantics { contentDescription = "Save this card" },
            color = color, size = 11.sp,
        )
        DropdownMenu(
            expanded = open, onDismissRequest = { open = false },
            shape = RectangleShape, containerColor = Poster.Stock, tonalElevation = 0.dp, shadowElevation = 0.dp, border = BorderStroke(2.dp, Poster.Ink),
        ) {
            for (f in ShareFormat.entries) DropdownMenuItem(text = { CapsText(f.label, color = Poster.Ink, size = 12.sp) }, onClick = { save(f) })
            share.text?.let { text ->
                DropdownMenuItem(text = { CapsText("Text", color = Poster.Ink, size = 12.sp) }, onClick = { open = false; context.findActivity()?.let { ShareSheet.shareText(it, text()) } })
            }
        }
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
