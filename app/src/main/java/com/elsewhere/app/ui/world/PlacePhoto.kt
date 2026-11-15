package com.elsewhere.app.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.elsewhere.app.data.Place

/**
 * The picture for a place: its Google photo at 4:3 on stock-2 while it loads. Renders nothing
 * without one, like the web's `PlacePhoto`; `blank` prints the striped "no photo on record" instead.
 */
@Composable
fun PlacePhoto(place: Place?, modifier: Modifier = Modifier, blank: Boolean = false) {
    val url = place?.photo
    if (url != null) {
        AsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Poster.Stock2),
        )
    } else if (blank) {
        val stripes = Brush.linearGradient(
            0f to Poster.Stock2, 0.5f to Poster.Stock2, 0.5f to Poster.Stock, 1f to Poster.Stock,
            start = androidx.compose.ui.geometry.Offset.Zero, end = androidx.compose.ui.geometry.Offset(28f, 28f), tileMode = TileMode.Repeated,
        )
        Box(modifier.fillMaxWidth().aspectRatio(4f / 3f).background(stripes), contentAlignment = Alignment.Center) {
            CapsText("no photo on record", size = 11.sp)
        }
    }
}
