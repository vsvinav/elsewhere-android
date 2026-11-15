package com.elsewhere.app.ui.photos

import com.elsewhere.app.photos.CAPTION_MAX
import com.elsewhere.app.photos.PhotoFiles
import com.elsewhere.app.photos.PhotoStore
import com.elsewhere.app.photos.PlaceCtx

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
data class SnapPlace(val id: String, val name: String, val lat: Double, val lng: Double, val city: String? = null, val address: String? = null) {
    val ctx get() = PlaceCtx(id, lat, lng, city)
}

/** A card on screen: the photo and, when known, the place it belongs to. */
data class OpenPhoto(val photo: PhotoEntity, val place: SnapPlace?)

/** Which picker to open once the privacy note is out of the way. */
enum class Source { CAMERA, GALLERY }

@HiltViewModel
class SnapViewModel @Inject constructor(val store: PhotoStore, val files: PhotoFiles) : ViewModel() {
    val photos: StateFlow<List<PhotoEntity>> = store.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The place and picker waiting on the privacy note; null when nothing is pending. */
    var privacyFor by mutableStateOf<Pair<SnapPlace?, Source>?>(null); private set
    var open by mutableStateOf<OpenPhoto?>(null); private set
    /** The place the camera was opened from, while it is up. */
    var pending: SnapPlace? = null; private set
    var cameraFile: File? = null; private set

    /** Resolves to the picker to launch, or null when the note has to be read first. */
    fun begin(place: SnapPlace?, source: Source, launch: (Source) -> Unit) {
        viewModelScope.launch {
            pending = place
            if (store.privacySeen()) launch(source) else privacyFor = place to source
        }
    }

    fun privacyRead(launch: (Source) -> Unit) {
        val (place, source) = privacyFor ?: return
        privacyFor = null
        viewModelScope.launch { store.markPrivacySeen(); pending = place; launch(source) }
    }

    fun privacyDismissed() { privacyFor = null; pending = null }

    fun cameraTarget(): Uri { val (f, uri) = files.cameraTarget(); cameraFile = f; return uri }

    fun picked(uri: Uri?) {
        val place = pending; pending = null
        val tmp = cameraFile; cameraFile = null
        if (uri == null) { tmp?.delete(); return }
        viewModelScope.launch {
            store.snap(uri, place?.ctx)?.let { open = OpenPhoto(it, place) }
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
