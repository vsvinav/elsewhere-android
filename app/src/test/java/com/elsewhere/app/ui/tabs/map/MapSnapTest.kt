package com.elsewhere.app.ui.tabs.map

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.data.db.PhotoEntity
import com.elsewhere.app.map.PinLayers
import com.elsewhere.app.map.PinOpts
import com.elsewhere.app.ui.photos.LocalSnapper
import com.elsewhere.app.ui.photos.SnapPlace
import com.elsewhere.app.ui.photos.Snapper
import com.elsewhere.app.ui.photos.Source
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.PhotoCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class MapSnapTest {
    @get:Rule val rule = createComposeRule()

    private class Recorder : Snapper {
        val snaps = ArrayList<SnapPlace?>()
        val deleted = ArrayList<String>()
        override val photos = mutableStateOf(emptyList<PhotoEntity>())
        override fun snap(place: SnapPlace?, source: Source, caption: String?) { snaps += place }
        override fun show(photo: PhotoEntity, place: SnapPlace?) {}
        override fun delete(id: String) { deleted += id; photos.value = photos.value.filter { it.id != id } }
        override fun thumb(photo: PhotoEntity): File? = null
    }

    @Test fun `SNAP on the open pin asks for a photo of that place, and its strip can delete one`() {
        val rec = Recorder()
        val layers = PinLayers(SampleRecord.record)
        val market = layers.all.first { it.id == "market" }
        rec.photos.value = listOf(PhotoEntity("ph-1", 0L, market.lat, market.lng, "market", market.place.city, null, "ph-1.jpg"))
        rule.setContent {
            CompositionLocalProvider(LocalSnapper provides rec) {
                ElsewhereTheme { MapScreen(layers, PinOpts(selected = "market"), MapActions()) { m -> Box(m) } }
            }
        }
        rule.onNodeWithContentDescription("Snap a photo here").assertIsDisplayed().performClick()
        assertEquals(1, rec.snaps.size)
        assertEquals("market", rec.snaps[0]!!.id)
        assertEquals(market.title, rec.snaps[0]!!.name)
        rule.onNodeWithContentDescription("Photos taken here").assertIsDisplayed()
        rule.onNodeWithContentDescription("Delete photo").performClick()
        assertEquals(listOf("ph-1"), rec.deleted)
        rule.onNodeWithContentDescription("Photos taken here").assertDoesNotExist()
    }

    @Test fun `the card prints the place, the day stamp, the coordinates, the caption and its barcode`() {
        val tz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val photo = PhotoEntity("ph-2", 1763208000000L, 38.7134, -9.1309, "lis-chafariz-xyz", "Lisbon", "Late light on the tiles", "ph-2.jpg")
            rule.setContent { ElsewhereTheme { PhotoCard(photo, null, "Chafariz d'El Rei") } }
            rule.onNodeWithText("CHAFARIZ D'EL REI").assertIsDisplayed()
            rule.onNodeWithText("LISBON").assertIsDisplayed()
            rule.onNodeWithText("15").assertIsDisplayed()
            rule.onNodeWithText("NOV 2025").assertIsDisplayed()
            rule.onNodeWithText("38.713, -9.131").assertIsDisplayed()
            rule.onNodeWithText("Late light on the tiles").assertExists()
            rule.onNodeWithText("PHO 20251115 LIS-CH").assertExists()
        } finally { TimeZone.setDefault(tz) }
    }
}
