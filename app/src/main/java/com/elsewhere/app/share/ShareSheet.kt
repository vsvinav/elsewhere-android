package com.elsewhere.app.share

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import java.io.File

/** Hands a card or a line of text to the system share sheet: the Android half of web/src/share/text.ts's `shareText`. */
object ShareSheet {
    private const val DIR = "share"

    /** Writes the PNG under `cacheDir/share/` and returns the content URI the file provider serves it at. */
    fun writeCard(context: Context, bitmap: Bitmap, file: String): Uri {
        val f = File(File(context.cacheDir, DIR).apply { mkdirs() }, file)
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    fun shareImage(activity: Activity, uri: Uri) {
        val b = ShareCompat.IntentBuilder(activity).setType("image/png").setStream(uri).setChooserTitle("elsewhere")
        b.intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(b.createChooserIntent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    fun shareText(activity: Activity, text: String) {
        ShareCompat.IntentBuilder(activity).setType("text/plain").setText(text).startChooser()
    }
}
