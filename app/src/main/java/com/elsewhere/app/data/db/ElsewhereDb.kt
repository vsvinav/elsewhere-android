package com.elsewhere.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [VisitEntity::class, ActivityEntity::class, PathEntity::class, RawFixEntity::class, MemoryEntity::class,
        FrequentPlaceEntity::class, PlaceEnrichmentEntity::class, ImportMetaEntity::class],
    version = 2, exportSchema = false,
)
abstract class ElsewhereDb : RoomDatabase() {
    abstract fun visits(): VisitDao
    abstract fun activities(): ActivityDao
    abstract fun paths(): PathDao
    abstract fun rawFixes(): RawFixDao
    abstract fun memories(): MemoryDao
    abstract fun frequentPlaces(): FrequentPlaceDao
    abstract fun placeEnrichment(): PlaceEnrichmentDao
    abstract fun importMeta(): ImportMetaDao
}

/** 1 → 2: rows learn where they came from, and the phone's own fixes get a table. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (t in listOf("visits", "activities", "paths")) db.execSQL("ALTER TABLE `$t` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'import'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `raw_fixes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ts` INTEGER NOT NULL, " +
                "`lat` REAL NOT NULL, `lng` REAL NOT NULL, `accuracyM` REAL NOT NULL, `speedMps` REAL, `altitude` REAL, `activity` TEXT, `batteryPct` INTEGER)",
        )
    }
}

val MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2)
