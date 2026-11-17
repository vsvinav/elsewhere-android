package com.elsewhere.app.ui.crew

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elsewhere.app.data.db.PhotoEntity
import com.elsewhere.app.record.NowStamp
import com.elsewhere.app.sonar.SonarHit
import com.elsewhere.app.ui.photos.LocalSnapper
import com.elsewhere.app.ui.photos.SnapPlace
import com.elsewhere.app.ui.photos.Snapper
import com.elsewhere.app.ui.photos.Source
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SonarSheetTest {
    @get:Rule val rule = createComposeRule()

    private class Recorder : Snapper {
        val snaps = ArrayList<Pair<SnapPlace?, String?>>()
        override val photos = mutableStateOf(emptyList<PhotoEntity>())
        override fun snap(place: SnapPlace?, source: Source, caption: String?) { snaps += place to caption }
        override fun show(photo: PhotoEntity, place: SnapPlace?) {}
        override fun delete(id: String) {}
        override fun thumb(photo: PhotoEntity): File? = null
    }

    private val hit = SonarHit(CrewFixtures.marta, "h1", CrewFixtures.NOW)
    private val here = NowStamp(38.72, -9.14, "19:04", "Lisbon", "just now")

    private fun show(hit: SonarHit, live: NowStamp?, rec: Recorder, onSnap: () -> Unit = {}, onDismiss: () -> Unit = {}) = rule.setContent {
        CompositionLocalProvider(LocalSnapper provides rec) { ElsewhereTheme { SonarSheetBody(hit, live, onDismiss, onSnap) } }
    }

    @Test fun `the sheet names the friend and snapping opens the camera captioned with them, where you are, and stays up`() {
        val rec = Recorder()
        var dismissed = 0; var snapped = 0
        show(hit, here, rec, onSnap = { snapped++ }) { dismissed++ }
        rule.onNodeWithText("Marta is within a mile", ignoreCase = true).assertIsDisplayed()
        rule.onNodeWithText(SONAR_SHEET_LINE).assertIsDisplayed()
        rule.onNodeWithText("Snap a photo together", ignoreCase = true).performClick()
        val (place, caption) = rec.snaps.single()
        assertEquals("with Marta", caption)
        assertEquals("sonar:h1", place!!.id)
        assertEquals("Lisbon", place.name)
        assertEquals("Lisbon", place.city)
        assertEquals(38.72, place.lat, 1e-9)
        assertEquals("the camera can take a while; the sheet waits for Dismiss", 0, dismissed)
        assertEquals(1, snapped)
        rule.onNodeWithText("Marta is within a mile", ignoreCase = true).assertIsDisplayed()
        rule.onNodeWithText("Dismiss", ignoreCase = true).performClick()
        assertEquals(1, dismissed)
    }

    @Test fun `away from any city the place is named for the friend`() {
        val rec = Recorder()
        show(hit, here.copy(city = ""), rec)
        rule.onNodeWithText("Snap a photo together", ignoreCase = true).performClick()
        val place = rec.snaps.single().first!!
        assertEquals("With Marta", place.name)
        assertNull(place.city)
    }

    @Test fun `a rehearsal ping says so, and with no fix the snap carries no place`() {
        val rec = Recorder()
        var dismissed = 0
        show(hit.copy(rehearsal = true), null, rec) { dismissed++ }
        rule.onNodeWithText("Rehearsal ping — Marta is within a mile.").assertIsDisplayed()
        rule.onNodeWithText(SONAR_SHEET_LINE).assertDoesNotExist()
        rule.onNodeWithText("Snap a photo together", ignoreCase = true).performClick()
        assertNull(rec.snaps.single().first)
        rule.onNodeWithText("Dismiss", ignoreCase = true).performClick()
        assertEquals(1, dismissed)
    }
}
