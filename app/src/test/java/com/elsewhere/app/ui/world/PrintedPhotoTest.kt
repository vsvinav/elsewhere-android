package com.elsewhere.app.ui.world

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.elsewhere.app.Fx
import com.elsewhere.app.share.CardRenderer
import com.elsewhere.app.share.LocalPrinting
import com.elsewhere.app.share.ShareFormat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrintedPhotoTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val place = Fx.place(name = "Louvre").copy(photo = "https://photos.example/louvre.jpg")

    @Test fun `a photo on a shared card tells the card which url it shows, by place`() {
        val photos = CardPhotos()
        rule.setContent { CompositionLocalProvider(LocalCardPhotos provides photos) { PlacePhoto(place) } }
        rule.waitForIdle()
        assertEquals(mapOf(photoKey(place) to "https://photos.example/louvre.jpg"), photos.urls)
    }

    @Test fun `a Commons photo found on screen prints too, though the off-screen card never gets to look it up`() {
        val museum = Fx.place(name = "Louvre", lat = 48.86, lng = 2.34, primaryType = "museum")
        val commons = PhotoSources(commons = { _, _ -> "https://commons.example/louvre.jpg" })
        val photos = CardPhotos()
        rule.setContent {
            CompositionLocalProvider(LocalCardPhotos provides photos, LocalPhotoSources provides commons) { PlacePhoto(museum) }
        }
        rule.waitForIdle()
        assertEquals(mapOf(photoKey(museum) to "https://commons.example/louvre.jpg"), photos.urls)

        val red = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFFF0000.toInt()) }
        val bmp = rule.runOnUiThread {
            CardRenderer.render(rule.activity, ShareFormat.STORY) {
                CompositionLocalProvider(LocalPhotoSources provides commons, LocalPrintedPhotos provides mapOf(photoKey(museum) to red.asImageBitmap())) {
                    Box(Modifier.fillMaxWidth()) { PlacePhoto(museum) }
                }
            }
        }
        assertEquals(0xFFFF0000.toInt(), bmp.getPixel(540, (1920 - 120) / 2))
    }

    @Test fun `while printing, a pre-fetched bitmap is drawn in place of the network image`() {
        val red = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFFF0000.toInt()) }
        val bmp = rule.runOnUiThread {
            CardRenderer.render(rule.activity, ShareFormat.STORY) {
                CompositionLocalProvider(LocalPrintedPhotos provides mapOf(photoKey(place) to red.asImageBitmap())) {
                    Box(Modifier.fillMaxWidth()) { PlacePhoto(place) }
                }
            }
        }
        // The 960px card is 720px tall at 4:3 and sits centred above the footer.
        assertEquals(0xFFFF0000.toInt(), bmp.getPixel(540, (1920 - 120) / 2))
    }

    private fun <T> androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.runOnUiThread(block: () -> T): T {
        var out: T? = null
        runOnUiThread { out = block() }
        @Suppress("UNCHECKED_CAST") return out as T
    }
}
