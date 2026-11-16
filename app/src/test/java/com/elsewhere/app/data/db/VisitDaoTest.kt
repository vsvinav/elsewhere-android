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
class VisitDaoTest {
    private lateinit var db: ElsewhereDb

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ElsewhereDb::class.java).allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    private fun visit(id: Int, day: Int, hour: Int, source: String = SOURCE_DEVICE) =
        VisitEntity(id, day * 86400L + hour * 3600L, day * 86400L + hour * 3600L + 1800L, 0, "p$id", 1.0, 2.0, "visit", 0.9, 0, null, null, null, source = source)

    @Test fun `recorded days counts distinct days of device stays only`() = runBlocking {
        assertEquals(0, db.visits().recordedDays())
        db.visits().insertAll(listOf(visit(1, 10, 8), visit(2, 10, 20), visit(3, 11, 9), visit(4, 13, 9), visit(5, 14, 9, source = SOURCE_IMPORT)))
        assertEquals(3, db.visits().recordedDays())
    }
}
