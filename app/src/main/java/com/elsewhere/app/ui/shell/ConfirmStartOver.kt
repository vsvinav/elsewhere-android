package com.elsewhere.app.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType

/** "Start over" throws the import and the answers away; ask once. */
@Composable
fun ConfirmStartOver(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Pass(Modifier.fillMaxWidth().padding(16.dp)) {
            CapsText("Start over")
            Spacer(Modifier.height(12.dp))
            DisplayText("Tear it up?", 40.sp)
            Spacer(Modifier.height(16.dp))
            Text("Your import and your answers leave this phone. Nothing was ever uploaded, so there is nothing else to clear.", color = Poster.Ink2, style = PosterType.Lede)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InkButton("Start over", onConfirm, red = true)
                InkButton("Keep it", onDismiss, quiet = true)
            }
        }
    }
}
