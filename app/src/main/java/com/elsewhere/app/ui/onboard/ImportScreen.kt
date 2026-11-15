package com.elsewhere.app.ui.onboard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.data.ImportReport
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import java.util.Locale

/** The front door's first page: the title, and a pass that takes the Timeline export. */
@Composable
fun ImportScreen(importing: Float?, error: String?, report: ImportReport?, onRecord: (() -> Unit)? = null, onFile: (Uri, String) -> Unit) {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onFile(uri, fileName(ctx, uri))
    }
    OnboardFrame(question = null, onCancel = null) {
        DisplayText("Elsewhere", 72.sp, misreg = true)
        Spacer(Modifier.height(28.dp))
        Text(
            "Your Google Maps Timeline, printed as a world-tour poster. Every city you slept in, every flight, every drive. It never leaves this phone.",
            color = Poster.Ink, style = PosterType.Lede.copy(fontSize = 18.sp),
        )
        Spacer(Modifier.height(36.dp))
        Pass(Modifier.fillMaxWidth().clickable(enabled = importing == null) { pick.launch(arrayOf("application/json", "*/*")) }) {
            if (importing != null) {
                CapsText("Reading segments…", color = Poster.Ink)
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).background(Poster.Stock2)) {
                    Box(Modifier.fillMaxWidth(importing.coerceIn(0f, 1f)).height(8.dp).background(Poster.Ink))
                }
                Spacer(Modifier.height(12.dp))
                Text("Big exports take a few seconds. Nothing is uploaded.", color = Poster.Ink3, style = PosterType.Label)
            } else {
                CapsText("Tap to choose your Timeline.json", color = Poster.Red)
                Spacer(Modifier.height(14.dp))
                Text(
                    "Android: Settings → Location → Location Services → Timeline → Export Timeline data.\niPhone: Google Maps → your photo → Your timeline → ⋯ → Export.",
                    color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 15.sp),
                )
                if (error != null) {
                    Spacer(Modifier.height(14.dp))
                    Text("Couldn't read that file: $error", color = Poster.Red2, style = PosterType.Lede.copy(fontSize = 14.sp))
                }
                if (report != null) {
                    Spacer(Modifier.height(14.dp))
                    val skipped = report.segmentsTotal - report.parsed
                    CapsText("${n(report.segmentsTotal)} segments read, ${n(skipped)} skipped", color = Poster.Ink2, size = 11.sp)
                }
            }
        }
        if (onRecord != null && importing == null) {
            Spacer(Modifier.height(24.dp))
            CapsText("Skip — record on this phone instead →", Modifier.clickable(onClick = onRecord), color = Poster.Ink2, size = 12.sp)
        }
    }
}

private fun n(x: Int) = String.format(Locale.US, "%,d", x)

private fun fileName(ctx: android.content.Context, uri: Uri): String {
    ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) return c.getString(0) ?: "Timeline.json"
    }
    return uri.lastPathSegment ?: "Timeline.json"
}
