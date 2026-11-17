package com.elsewhere.app.ui.photos

import com.elsewhere.app.photos.CAPTION_MAX
import com.elsewhere.app.photos.PhotoFiles
import com.elsewhere.app.photos.PhotoStore
import com.elsewhere.app.photos.PlaceCtx

import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.data.db.PhotoEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** The place a snap was opened from: what the card prints and how the photo is filed. */
@Serializable
data class SnapPlace(val id: String, val name: String, val lat: Double, val lng: Double, val city: String? = null, val address: String? = null) {
    val ctx get() = PlaceCtx(id, lat, lng, city)
}

/** A card on screen: the photo and, when known, the place it belongs to. */
data class OpenPhoto(val photo: PhotoEntity, val place: SnapPlace?)

/** Which picker to open once the privacy note is out of the way. */
enum class Source { CAMERA, GALLERY }

@HiltViewModel
class SnapViewModel @Inject constructor(val store: PhotoStore, val files: PhotoFiles, private val state: SavedStateHandle) : ViewModel() {
    val photos: StateFlow<List<PhotoEntity>> = store.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The place and picker waiting on the privacy note; null when nothing is pending. */
    var privacyFor by mutableStateOf<Pair<SnapPlace?, Source>?>(null); private set
    var open by mutableStateOf<OpenPhoto?>(null); private set
    /** The place the camera was opened from, while it is up; saved so a process death behind the camera loses nothing. */
    var pending: SnapPlace?
        get() = state.get<String>(PENDING)?.let { Json.decodeFromString<SnapPlace>(it) }
        private set(v) { state[PENDING] = v?.let { Json.encodeToString(SnapPlace.serializer(), it) } }
    var cameraFile: File?
        get() = state.get<String>(CAMERA_FILE)?.let(::File)
        private set(v) { state[CAMERA_FILE] = v?.path }
    /** A caption to write on the photo once it is taken, e.g. the friend a Sonar hit was snapped with. */
    var pendingCaption: String?
        get() = state.get<String>(PENDING_CAPTION)
        private set(v) { state[PENDING_CAPTION] = v }

    /** Resolves to the picker to launch, or null when the note has to be read first. */
    fun begin(place: SnapPlace?, source: Source, caption: String? = null, launch: (Source) -> Unit) {
        viewModelScope.launch {
            pending = place; pendingCaption = caption
            if (store.privacySeen()) launch(source) else privacyFor = place to source
        }
    }

    fun privacyRead(launch: (Source) -> Unit) {
        val (place, source) = privacyFor ?: return
        privacyFor = null
        viewModelScope.launch { store.markPrivacySeen(); pending = place; launch(source) }
    }

    fun privacyDismissed() { privacyFor = null; pending = null; pendingCaption = null }

    fun cameraTarget(): Uri { val (f, uri) = files.cameraTarget(); cameraFile = f; return uri }

    fun picked(uri: Uri?) {
        val place = pending; pending = null
        val caption = pendingCaption?.take(CAPTION_MAX)?.ifBlank { null }; pendingCaption = null
        val tmp = cameraFile; cameraFile = null
        if (uri == null) { tmp?.delete(); return }
        viewModelScope.launch {
            store.snap(uri, place?.ctx)?.let {
                if (caption != null) store.caption(it.id, caption)
                open = OpenPhoto(it.copy(caption = caption ?: it.caption), place)
            }
            tmp?.delete()
        }
    }

    fun show(photo: PhotoEntity, place: SnapPlace?) { open = OpenPhoto(photo, place) }
    fun close() { open = null }

    fun caption(text: String) {
        val o = open ?: return
        val clipped = text.take(CAPTION_MAX)
        open = o.copy(photo = o.photo.copy(caption = clipped.ifBlank { null }))
        viewModelScope.launch { store.caption(o.photo.id, clipped) }
    }

    fun delete(id: String) {
        if (open?.photo?.id == id) open = null
        viewModelScope.launch { store.delete(id) }
    }
}

private const val PENDING = "snap.pending"
private const val CAMERA_FILE = "snap.cameraFile"
private const val PENDING_CAPTION = "snap.caption"
