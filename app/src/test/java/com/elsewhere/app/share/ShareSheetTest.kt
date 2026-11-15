package com.elsewhere.app.share

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareSheetTest {
    private val app = ApplicationProvider.getApplicationContext<android.content.Context>()

    // One test: FileProvider caches its roots statically, and Robolectric gives each test a fresh data dir.
    @Test fun `a card is written under the share cache and sent as a png the receiver may read`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val uri = ShareSheet.writeCard(app, Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), "elsewhere-x-story.png")
        assertEquals("content://com.elsewhere.app.files/share/elsewhere-x-story.png", uri.toString())
        assertTrue(File(app.cacheDir, "share/elsewhere-x-story.png").length() > 0)
        ShareSheet.shareImage(activity, uri)
        val chooser = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("image/png", send.type)
        assertEquals(uri, send.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun `sharing text sends plain text`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        ShareSheet.shareText(activity, "hello\n$SITE")
        val send = shadowOf(activity).nextStartedActivity.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals("text/plain", send.type)
        assertEquals("hello\n$SITE", send.getStringExtra(Intent.EXTRA_TEXT))
    }
}
