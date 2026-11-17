package com.elsewhere.app.ui.photos

import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.elsewhere.app.data.db.PhotoEntity
import com.elsewhere.app.photos.CAPTION_MAX
import com.elsewhere.app.photos.PRIVACY_COPY
import com.elsewhere.app.share.CardRenderer
import com.elsewhere.app.share.ShareFormat
import com.elsewhere.app.share.cardFileName
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.PhotoCard
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterTextField
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// The Android half of web/src/photos/Snap.tsx: a SNAP stamp on the open pin, "Snap a photo here" on an opened place,
// the note before the first one, the card afterwards, and a strip of what was taken there.

/** What the entry points talk to; the host below provides the real one, tests a recorder. */
interface Snapper {
    val photos: State<List<PhotoEntity>>
    fun snap(place: SnapPlace?, source: Source = Source.CAMERA, caption: String? = null)
    fun show(photo: PhotoEntity, place: SnapPlace?)
    fun delete(id: String)
    fun thumb(photo: PhotoEntity): File?
}

val LocalSnapper = staticCompositionLocalOf<Snapper?> { null }

/** The round red SNAP stamp on an open pin's tag. */
@Composable
fun SnapStamp(place: SnapPlace, modifier: Modifier = Modifier) {
    val snapper = LocalSnapper.current
    Box(
        modifier.size(56.dp).rotate(-12f).background(Poster.Stock, CircleShape).border(3.dp, Poster.Red, CircleShape).padding(3.dp).border(1.dp, Poster.Red, CircleShape)
            .clickable(role = Role.Button) { snapper?.snap(place) }.semantics { contentDescription = "Snap a photo here" },
        contentAlignment = Alignment.Center,
    ) {
        Text("SNAP", color = Poster.Red, textAlign = TextAlign.Center, style = PosterType.Display.copy(fontSize = 15.sp, letterSpacing = 0.1.em, lineHeight = 1.em))
    }
}

/** "Snap a photo here" / "From gallery" under an opened place. */
@Composable
fun SnapButtons(place: SnapPlace, modifier: Modifier = Modifier) {
    val snapper = LocalSnapper.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InkButton("Snap a photo here", { snapper?.snap(place) }, red = true)
        InkButton("Gallery", { snapper?.snap(place, Source.GALLERY) }, quiet = true)
    }
}

/** Thumbnails of what was snapped at a place, each with a way to delete it; nothing when there are none. */
@Composable
fun PhotoStrip(place: SnapPlace, modifier: Modifier = Modifier, compact: Boolean = false) {
    val snapper = LocalSnapper.current ?: return
    val mine = snapper.photos.value.filter { it.placeId == place.id }
    if (mine.isEmpty()) return
    val side = if (compact) 48.dp else 84.dp
    Row(modifier.semantics { contentDescription = "Photos taken here" }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (p in mine) {
            Box(Modifier.padding(top = 6.dp, end = 6.dp)) {
                val bmp by rememberBitmap(snapper.thumb(p))
                Box(Modifier.size(side).rotate(-2f).background(Poster.Stock2).border(2.dp, Poster.Ink).clickable(role = Role.Button) { snapper.show(p, place) }.semantics { contentDescription = "Open photo" }) {
                    bmp?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(side)) }
                }
                Text(
                    "×", color = Poster.Stock, style = PosterType.Display.copy(fontSize = 14.sp, lineHeight = 1.em), textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(20.dp).background(Poster.Ink, CircleShape)
                        .clickable(role = Role.Button) { snapper.delete(p.id) }.semantics { contentDescription = "Delete photo" }.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
fun rememberBitmap(file: File?): State<ImageBitmap?> = produceState<ImageBitmap?>(null, file) {
    value = file?.let { f -> withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() } }
}

/** Wraps a tab so its SNAP stamps and strips work: pickers, the first-time note and the card live here. */
@Composable
fun SnapHost(vm: SnapViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val photos = vm.photos.collectAsState()
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> vm.picked(if (ok) vm.cameraFile?.let(android.net.Uri::fromFile) else null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { vm.picked(it) }
    val launch = { s: Source ->
        when (s) {
            Source.CAMERA -> camera.launch(vm.cameraTarget())
            Source.GALLERY -> gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    val snapper = remember(vm) {
        object : Snapper {
            override val photos get() = photos
            override fun snap(place: SnapPlace?, source: Source, caption: String?) = vm.begin(place, source, caption, launch)
            override fun show(photo: PhotoEntity, place: SnapPlace?) = vm.show(photo, place)
            override fun delete(id: String) = vm.delete(id)
            override fun thumb(photo: PhotoEntity) = vm.files.thumb(photo.id)
        }
    }
    CompositionLocalProvider(LocalSnapper provides snapper) { content() }
    if (vm.privacyFor != null) PrivacySheet(onRead = { vm.privacyRead(launch) }, onDismiss = vm::privacyDismissed)
    vm.open?.let { o ->
        PhotoSheet(o, picture = vm.files.original(o.photo.id), onCaption = vm::caption, onDelete = { vm.delete(o.photo.id) }, onClose = vm::close, saveToPhotos = vm.files::saveToPhotos)
    }
}

/** Shown once, before the first snap. */
@Composable
fun PrivacySheet(onRead: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Pass(Modifier.fillMaxWidth().padding(16.dp)) {
            CapsText("Before your first snap")
            Spacer(Modifier.height(12.dp))
            DisplayText("Yours only", 40.sp)
            Spacer(Modifier.height(16.dp))
            Text(PRIVACY_COPY, color = Poster.Ink2, style = PosterType.Lede)
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InkButton("Got it, open the camera", onRead, red = true)
                InkButton("Not now", onDismiss, quiet = true)
            }
        }
    }
}

/** The card for one photo, with its caption to edit, the Save menu, "Save to Photos" and delete. */
@Composable
fun PhotoSheet(
    o: OpenPhoto, picture: File?, onCaption: (String) -> Unit, onDelete: () -> Unit, onClose: () -> Unit,
    saveToPhotos: (android.graphics.Bitmap, String) -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bmp by rememberBitmap(picture)
    val name = o.place?.name ?: o.photo.city ?: "Somewhere"
    var caption by remember(o.photo.id) { mutableStateOf(o.photo.caption ?: "") }
    var saved by remember { mutableStateOf<String?>(null) }
    val card: @Composable (Share?) -> Unit = { share -> PhotoCard(o.photo, bmp, name, o.place?.address, share = share) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()).semantics { contentDescription = "Your photo" }) {
            card(Share(name))
            Spacer(Modifier.height(12.dp))
            Pass(Modifier.fillMaxWidth()) {
                // Committed when the field loses focus or on Done, like the web's blur/Enter, not on every keystroke.
                val commit = { if (caption != (o.photo.caption ?: "")) onCaption(caption) }
                PosterTextField(
                    caption, { caption = it.take(CAPTION_MAX) }, Modifier.onFocusChanged { if (!it.isFocused) commit() },
                    label = "Caption · ${caption.length}/$CAPTION_MAX", placeholder = "A line for the card",
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    InkButton("Done", { commit(); onClose() })
                    InkButton(saved ?: "Save to Photos", {
                        val activity = context.findActivity() ?: return@InkButton
                        scope.launch {
                            val ok = runCatching {
                                val bitmap = CardRenderer.render(activity, ShareFormat.FREE) { card(null) }
                                withContext(Dispatchers.IO) { saveToPhotos(bitmap, cardFileName(name, ShareFormat.FREE)) }
                            }.onFailure { Log.e("PhotoSheet", "Couldn't save to Photos", it) }.getOrDefault(false)
                            saved = if (ok) "Saved" else "Couldn't save"
                        }
                    }, quiet = true)
                }
                Spacer(Modifier.height(12.dp))
                InkButton("Delete photo", onDelete, quiet = true, red = true)
                Spacer(Modifier.height(16.dp))
                Text(PRIVACY_COPY, color = Poster.Ink3, style = PosterType.Label)
            }
        }
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
