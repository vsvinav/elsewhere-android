package com.elsewhere.app.ui.world

import android.content.Context
import android.util.Log
import android.content.ContextWrapper
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import coil.imageLoader
import coil.decode.DataSource
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Port of the web's SaveMenu (web/src/world/index.tsx): a quiet "Save" on every bill and pass.

/** How long the web's "Couldn't save" stays in the button. */
private const val FAILED_FOR_MS = 2500L

/** What a bill or pass offers to share: the name its file takes and, where there is one, a text version. */
class Share(val name: String, val text: (() -> String)? = null)

/**
 * "SAVE ▾" in the corner of a card, opening Free / Story / Post (and Text when the card has one).
 * `card` is the card as it should print, without this menu; it is composed off-screen on `paper` (the brand line in `ink`) and
 * shared as a PNG. `photos` lists the pictures the card shows, fetched up front since the print is one draw.
 */
@Composable
fun SaveMenu(
    share: Share, photos: CardPhotos, card: @Composable () -> Unit, modifier: Modifier = Modifier,
    color: Color = LocalMuted.current, paper: Color = Poster.Stock, ink: Color = Poster.Ink,
) {
    if (LocalPrinting.current) return
    val context = LocalContext.current
    val sources = LocalPhotoSources.current
    val scope = rememberCoroutineScope()
    val printed by rememberUpdatedState(card)
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun save(format: ShareFormat) {
        val activity = context.findActivity() ?: return
        open = false; busy = true
        scope.launch {
            try {
                val pictures = photos.urls.toMap().mapValues { (_, url) -> fetchPhoto(activity, url, url in photos.google, sources) }.filterValues { it != null }.mapValues { it.value!! }
                val bitmap = CardRenderer.render(activity, format, paper, ink) {
                    CompositionLocalProvider(LocalPhotoSources provides sources, LocalPrintedPhotos provides pictures) { printed() }
                }
                val uri = withContext(Dispatchers.IO) { ShareSheet.writeCard(activity, bitmap, cardFileName(share.name, format)) }
                ShareSheet.shareImage(activity, uri)
                busy = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SaveMenu", "Couldn't save ${share.name}", e)
                busy = false; failed = true
                delay(FAILED_FOR_MS); failed = false
            }
        }
    }

    Box(modifier) {
        CapsText(
            when { busy -> "Saving…"; failed -> "Couldn't save"; else -> "Save ▾" },
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

/**
 * A software bitmap (the sheet is drawn off-screen, where hardware bitmaps cannot be read) or null when the fetch fails.
 * A Google photo that had to go out to the network is billed, as on screen.
 */
private suspend fun fetchPhoto(context: Context, url: String, google: Boolean, sources: PhotoSources): ImageBitmap? {
    val result = context.imageLoader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).build()) as? SuccessResult ?: return null
    if (google && result.dataSource == DataSource.NETWORK) sources.onGooglePhoto()
    return (result.drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
