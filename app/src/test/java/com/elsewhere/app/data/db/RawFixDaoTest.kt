package com.elsewhere.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RawFixDaoTest {
    private lateinit var db: ElsewhereDb

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ElsewhereDb::class.java).allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    private fun fix(ts: Long) = RawFixEntity(ts = ts, lat = 1.0, lng = 2.0, accuracyM = 10f, speedMps = null, altitude = null, activity = null, batteryPct = null)

    @Test fun `since and prune work by timestamp`() = runBlocking {
        db.rawFixes().insertAll(listOf(fix(100), fix(200), fix(300)))
        assertEquals(3, db.rawFixes().countAll())
        assertEquals(listOf(200L, 300L), db.rawFixes().since(200).map { it.ts })
        db.rawFixes().pruneBefore(250)
        assertEquals(listOf(300L), db.rawFixes().since(0).map { it.ts })
        assertEquals(300L, db.rawFixes().latest()?.ts)
    }
}
