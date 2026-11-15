package com.elsewhere.app.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.elsewhere.app.analytics.Cat
import com.elsewhere.app.analytics.categoryOf
import com.elsewhere.app.data.Place

/** Kinds of place a geotagged Commons photo tends to actually show; web `PlacePhoto.tsx` FREE_FIRST. */
val FREE_FIRST = setOf(Cat.CULTURE, Cat.TRANSIT, Cat.STAY)

/**
 * The picture for a place: its Google photo at 4:3 on stock-2 while it loads, else for landmarks,
 * stations and hotels a free Wikimedia Commons one. Renders nothing without either, like the web's
 * `PlacePhoto`; `blank` prints the striped "no photo on record" instead.
 */
@Composable
fun PlacePhoto(place: Place?, modifier: Modifier = Modifier, blank: Boolean = false) {
    val sources = LocalPhotoSources.current
    val google = place?.photo
    val free = place != null && google == null && sources.commons != null && categoryOf(place) in FREE_FIRST
    var wiki by remember(place?.lat, place?.lng, free) { mutableStateOf<String?>(null) }
    if (free) LaunchedEffect(place!!.lat, place.lng) { wiki = runCatching { sources.commons!!(place.lat, place.lng) }.getOrNull() }
    val url = google ?: wiki
    if (url != null) {
        AsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Crop,
            onSuccess = { if (google != null) sources.onGooglePhoto() },
            modifier = modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Poster.Stock2),
        )
    } else if (blank && !free) {
        val stripes = Brush.linearGradient(
            0f to Poster.Stock2, 0.5f to Poster.Stock2, 0.5f to Poster.Stock, 1f to Poster.Stock,
            start = androidx.compose.ui.geometry.Offset.Zero, end = androidx.compose.ui.geometry.Offset(28f, 28f), tileMode = TileMode.Repeated,
        )
        Box(modifier.fillMaxWidth().aspectRatio(4f / 3f).background(stripes), contentAlignment = Alignment.Center) {
            CapsText("no photo on record", size = 11.sp)
        }
    }
}
