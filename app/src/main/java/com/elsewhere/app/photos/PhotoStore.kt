package com.elsewhere.app.photos

import android.content.Context
import android.net.Uri
import com.elsewhere.app.data.SettingsStore
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.data.db.PhotoEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Photos the user snapped at places: rows in Room, pixels in [PhotoFiles]. The web half is web/src/photos/store.ts. */
@Singleton
class PhotoStore @Inject constructor(
    @ApplicationContext private val ctx: Context, private val db: ElsewhereDb, private val files: PhotoFiles, private val settings: SettingsStore,
) {
    /** Swapped by tests; the clock the stamp reads. */
    var clock: () -> Long = System::currentTimeMillis

    val all: Flow<List<PhotoEntity>> get() = db.photos().all()

    suspend fun list(): List<PhotoEntity> = db.photos().list()
    suspend fun forPlace(placeId: String): List<PhotoEntity> = db.photos().forPlace(placeId)

    /**
     * Keeps the picture at `uri`: downsized on disk, placed by the place it was snapped from, else the phone's last fix
     * if fresh, else its EXIF. Returns the row, or null when the picture could not be read.
     */
    suspend fun snap(uri: Uri, place: PlaceCtx?): PhotoEntity? = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val open = { ctx.contentResolver.openInputStream(uri) }
        if (!files.keep(id, open)) return@withContext null
        val now = clock()
        val fix = db.rawFixes().latest()?.let { Fix(it.lat, it.lng, it.ts) }
        val exif = if (place == null) files.exif(open) else null
        val m = resolveMeta(place, fix, exif, now)
        val row = PhotoEntity(id, m.ts, m.lat, m.lng, m.placeId, m.city, null, files.original(id).name)
        db.photos().upsert(row)
        row
    }

    suspend fun caption(id: String, caption: String?) {
        val row = db.photos().get(id) ?: return
        db.photos().upsert(row.copy(caption = caption?.take(CAPTION_MAX)?.ifBlank { null }))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { db.photos().delete(id); files.delete(id) }

    suspend fun privacySeen(): Boolean = settings.photosPrivacySeen.first()
    suspend fun markPrivacySeen() { settings.markPhotosPrivacySeen() }
}
