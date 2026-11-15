package com.elsewhere.app.photos

import java.util.Calendar
import java.util.Locale

// Where and when a photo was taken, settled from what we know. Pure; the web half is web/src/photos/meta.ts.

/** A fix from the phone counts only while it is this fresh. */
const val FIX_MAX_AGE_MS = 10 * 60 * 1000L

data class PlaceCtx(val id: String, val lat: Double, val lng: Double, val city: String? = null)
data class Fix(val lat: Double, val lng: Double, val ts: Long)
data class Exif(val lat: Double, val lng: Double, val ts: Long? = null)
data class PhotoMeta(val ts: Long, val lat: Double? = null, val lng: Double? = null, val placeId: String? = null, val city: String? = null)

/**
 * Order of truth: the place the camera was opened from, a recent fix, the picture's own EXIF, nothing.
 * The time is `now`, except for a gallery picture placed by its EXIF, which keeps the moment it was taken.
 */
fun resolveMeta(place: PlaceCtx? = null, fix: Fix? = null, exif: Exif? = null, now: Long): PhotoMeta {
    if (place != null) return PhotoMeta(now, place.lat, place.lng, place.id, place.city)
    if (fix != null && now - fix.ts in 0..FIX_MAX_AGE_MS) return PhotoMeta(now, fix.lat, fix.lng)
    if (exif != null) return PhotoMeta(exif.ts ?: now, exif.lat, exif.lng)
    return PhotoMeta(now)
}

private val MONTHS = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

data class StampLines(val top: String, val big: String, val bottom: String)

private fun cal(ts: Long): Calendar = Calendar.getInstance().apply { timeInMillis = ts }

/** The round stamp's three lines: city or the brand, the day, month and year. Local time, like the phone's clock. */
fun stampLines(ts: Long, city: String?): StampLines {
    val c = cal(ts)
    return StampLines((city ?: "ELSEWHERE").uppercase(), c.get(Calendar.DAY_OF_MONTH).toString(), "${MONTHS[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}")
}

/** `PHO 20251115 LISCHA`: the day and the first six of the place id, or a blank run when there is no place. */
fun photoBarcode(ts: Long, placeId: String?): String {
    val c = cal(ts)
    val day = String.format(Locale.ROOT, "%04d%02d%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    return "PHO $day ${(placeId ?: "SOMEWH").take(6).uppercase()}"
}

/** The line under the name: the address, else the coordinates to three places, else a note that nothing was attached. */
fun whereLine(lat: Double?, lng: Double?, address: String? = null): String {
    if (!address.isNullOrEmpty()) return address.uppercase()
    if (lat != null && lng != null) return String.format(Locale.ROOT, "%.3f, %.3f", lat, lng)
    return "NO LOCATION ATTACHED"
}

const val CAPTION_MAX = 80
const val PRIVACY_COPY = "Photos stay on this device. The card you share is a picture with the place name and date on it — no GPS is attached. Delete any photo from its card; \"Start over\" removes them all."
