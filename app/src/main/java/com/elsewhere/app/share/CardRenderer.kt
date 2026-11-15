package com.elsewhere.app.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Poster

/** Prints a [ShareCard] to pixels without showing it: the Android half of web/src/share/saveCard.ts. */
object CardRenderer {
    /**
     * Composes `content` on a `format` sheet in an invisible view hung off the activity's content, measures
     * it at the sheet width, draws it, and takes the view down again. Main thread; returns synchronously
     * because the first composition is.
     */
    fun render(activity: ComponentActivity, format: ShareFormat, paper: Color = Poster.Stock, ink: Color = Poster.Ink, content: @Composable () -> Unit): Bitmap {
        val host = activity.findViewById<ViewGroup>(android.R.id.content)
        val view = ComposeView(activity).apply {
            alpha = 0f
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent { ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false) { ShareCard(format, paper, ink, content) } } }
        }
        try {
            host.addView(view, ViewGroup.LayoutParams(format.sheetWidth, ViewGroup.LayoutParams.WRAP_CONTENT))
            view.measure(
                View.MeasureSpec.makeMeasureSpec(format.sheetWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            return bitmap
        } finally {
            host.removeView(view)
        }
    }
}
