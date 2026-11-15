package com.elsewhere.app.record

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidServiceLinkTest {
    private class Gate(val location: Boolean) : PermissionGate {
        override val sdk = 34
        override fun granted(permission: String) = location || permission != Manifest.permission.ACCESS_FINE_LOCATION
        override fun ignoringBatteryOptimizations() = true
    }

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `start asks for the service when location is granted`() {
        AndroidServiceLink(app, Gate(true)).start()
        assertEquals(RecordService::class.java.name, shadowOf(app).nextStartedService.component!!.className)
    }

    @Test fun `start without location neither launches nor nags`() {
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.notify(RecordService.NUDGE_ID, androidx.core.app.NotificationCompat.Builder(app, RecordService.CHANNEL).build())
        AndroidServiceLink(app, Gate(false)).start()
        assertNull(shadowOf(app).nextStartedService)
        assertEquals(0, shadowOf(nm).allNotifications.size)
    }
}
