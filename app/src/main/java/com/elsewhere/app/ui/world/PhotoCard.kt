package com.elsewhere.app.ui.world

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.data.db.PhotoEntity
import com.elsewhere.app.photos.photoBarcode
import com.elsewhere.app.photos.stampLines
import com.elsewhere.app.photos.whereLine

/**
 * A snapped photo as a card: the picture on stock in an ink frame, tilted like a sticker, the day stamped top-right,
 * the place in Big Shoulders, where it was in caps, the caption, a barcode. Twin of web/src/world/PhotoCard.tsx;
 * saves and shares through [Pass] when `share` is given.
 */
@Composable
fun PhotoCard(photo: PhotoEntity, picture: ImageBitmap?, name: String, address: String? = null, modifier: Modifier = Modifier, share: Share? = null) {
    val stamp = stampLines(photo.ts, photo.city)
    Pass(modifier, share = share) {
        Box(Modifier.fillMaxWidth().padding(top = 16.dp, end = 8.dp)) {
            Box(Modifier.fillMaxWidth().rotate(1.5f).background(Poster.Stock).border(3.dp, Poster.Ink).padding(3.dp)) {
                if (picture != null) {
                    Image(picture, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).background(Poster.Stock2))
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Poster.Stock2))
                }
            }
            RecordStamp(stamp.top, stamp.big, stamp.bottom, Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-16).dp), size = 124.dp)
        }
        Spacer(Modifier.height(18.dp))
        Text(name.uppercase(), color = Poster.Ink, style = PosterType.Display.copy(fontSize = 36.sp, fontWeight = FontWeight.Black, lineHeight = 0.9.em), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        CapsText(whereLine(photo.lat, photo.lng, address), size = 11.sp)
        photo.caption?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 15.sp))
        }
        Spacer(Modifier.height(20.dp))
        Barcode(photoBarcode(photo.ts, photo.placeId))
    }
}
