package com.elsewhere.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Builds a version-1 database by hand, migrates it, and lets Room validate the result on open. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migrate-test.db"
    private var db: ElsewhereDb? = null

    @After fun tearDown() { db?.close(); ctx.deleteDatabase(name) }

    private val v1 = listOf(
        "CREATE TABLE IF NOT EXISTS `visits` (`id` INTEGER NOT NULL, `start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `tz` INTEGER NOT NULL, `placeId` TEXT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `type` TEXT NOT NULL, `p` REAL NOT NULL, `lvl` INTEGER NOT NULL, `city` TEXT, `cc` TEXT, `dist` REAL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `activities` (`id` INTEGER NOT NULL, `start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `tz` INTEGER NOT NULL, `sLat` REAL NOT NULL, `sLng` REAL NOT NULL, `eLat` REAL NOT NULL, `eLng` REAL NOT NULL, `m` REAL NOT NULL, `mode` TEXT NOT NULL, `p` REAL NOT NULL, `eCity` TEXT, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `paths` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `pts` BLOB NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `memories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `km` REAL NOT NULL, `dests` TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `frequent_places` (`placeId` TEXT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `label` TEXT, PRIMARY KEY(`placeId`))",
        "CREATE TABLE IF NOT EXISTS `place_enrichment` (`placeId` TEXT NOT NULL, `name` TEXT, `address` TEXT, `types` TEXT NOT NULL, `primaryType` TEXT, `photo` TEXT, PRIMARY KEY(`placeId`))",
        "CREATE TABLE IF NOT EXISTS `import_meta` (`id` INTEGER NOT NULL, `sourceFile` TEXT NOT NULL, `sourceSha256` TEXT NOT NULL, `builtAt` INTEGER NOT NULL, `segmentsTotal` INTEGER NOT NULL, `parsed` INTEGER NOT NULL, `skippedJson` TEXT NOT NULL, PRIMARY KEY(`id`))",
    )

    @Test fun `v1 rows migrate to v3 with source = import and empty raw_fixes and photos tables`() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(name).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    v1.forEach(db::execSQL)
                    db.execSQL("INSERT INTO visits VALUES (0, 100, 200, 0, 'p1', 1.0, 2.0, 'HOME', 0.9, 0, NULL, NULL, NULL)")
                    db.execSQL("INSERT INTO activities VALUES (1, 200, 300, 0, 1.0, 2.0, 1.1, 2.1, 500.0, 'WALKING', 0.9, NULL)")
                    db.execSQL("INSERT INTO paths (start, end, pts) VALUES (200, 300, X'00')")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }).build(),
        )
        helper.writableDatabase.close()

        val migrated = Room.databaseBuilder(ctx, ElsewhereDb::class.java, name).addMigrations(*MIGRATIONS).allowMainThreadQueries().build()
        db = migrated
        runBlocking {
            assertEquals(listOf("import"), migrated.visits().all().map { it.source })
            assertEquals(listOf("import"), migrated.activities().all().map { it.source })
            assertEquals(listOf("import"), migrated.paths().all().map { it.source })
            assertEquals(0, migrated.rawFixes().countAll())
            assertEquals(emptyList<PhotoEntity>(), migrated.photos().list())
            migrated.photos().upsert(PhotoEntity("p", 1L, null, null, "place", null, null, "p.jpg"))
            assertEquals("place", migrated.photos().get("p")?.placeId)
        }
    }
}
