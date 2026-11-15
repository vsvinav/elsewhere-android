package com.elsewhere.app.photos

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** The long edge a kept photo is cut down to, and the thumbnail's. */
const val PHOTO_MAX_PX = 2048
const val THUMB_MAX_PX = 512

/**
 * Photos on disk: originals under `filesDir/photos/originals/`, thumbnails under `filesDir/photos/thumbs/`, both `<id>.jpg`.
 * Originals are left out of backup (see res/xml), thumbnails and the database go along.
 */
@Singleton
class PhotoFiles @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val root get() = File(ctx.filesDir, "photos")
    private val originals get() = File(root, "originals")
    private val thumbs get() = File(root, "thumbs")

    fun original(id: String) = File(originals, "$id.jpg")
    fun thumb(id: String) = File(thumbs, "$id.jpg")

    /** Where the camera writes before the picture is kept; served to it through the file provider. */
    fun cameraTarget(): Pair<File, Uri> {
        val f = File(File(ctx.cacheDir, "camera").apply { mkdirs() }, "snap-${System.currentTimeMillis()}.jpg")
        return f to FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }

    /** What the picture itself says about where and when it was taken. */
    fun exif(open: () -> InputStream?): Exif? = runCatching {
        open()?.use { s ->
            val x = ExifInterface(s)
            val ll = x.latLong ?: return null
            Exif(ll[0], ll[1], x.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let(::parseExifDate))
        }
    }.getOrNull()

    /** Decodes, downsizes to [PHOTO_MAX_PX] and straightens by the EXIF orientation; writes the original and a thumbnail. */
    fun keep(id: String, open: () -> InputStream?): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return false
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(maxOf(bounds.outWidth, bounds.outHeight), PHOTO_MAX_PX) }
        var bmp = open()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
        val rotation = open()?.use { runCatching { ExifInterface(it).rotationDegrees }.getOrDefault(0) } ?: 0
        bmp = fit(bmp, PHOTO_MAX_PX, rotation)
        originals.mkdirs(); thumbs.mkdirs()
        original(id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        thumb(id).outputStream().use { fit(bmp, THUMB_MAX_PX, 0).compress(Bitmap.CompressFormat.JPEG, 80, it) }
        return true
    }

    fun delete(id: String) { original(id).delete(); thumb(id).delete() }

    fun clear() { root.deleteRecursively() }

    /** Drops a rendered card into the phone's Pictures, under an "Elsewhere" album. */
    fun saveToPhotos(bitmap: Bitmap, name: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Elsewhere")
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching { resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            .onFailure { resolver.delete(uri, null, null) }.isSuccess
    }
}

private fun sampleSize(longEdge: Int, max: Int): Int {
    var s = 1
    while (longEdge / (s * 2) >= max) s *= 2
    return s
}

private fun fit(b: Bitmap, max: Int, rotation: Int): Bitmap {
    val long = maxOf(b.width, b.height)
    val k = if (long > max) max.toFloat() / long else 1f
    if (k == 1f && rotation == 0) return b
    val m = Matrix().apply { postScale(k, k); if (rotation != 0) postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
}

/** EXIF's `yyyy:MM:dd HH:mm:ss`, read as local time like the phone that took it. */
internal fun parseExifDate(s: String): Long? =
    runCatching { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ROOT).apply { timeZone = TimeZone.getDefault() }.parse(s)?.time }.getOrNull()
