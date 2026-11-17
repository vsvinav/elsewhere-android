package com.elsewhere.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [VisitEntity::class, ActivityEntity::class, PathEntity::class, RawFixEntity::class, MemoryEntity::class,
        FrequentPlaceEntity::class, PlaceEnrichmentEntity::class, ImportMetaEntity::class, PhotoEntity::class,
        SyncStateEntity::class],
    version = 4, exportSchema = true,
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
    abstract fun photos(): PhotoDao
    abstract fun syncState(): SyncStateDao
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

/** 2 → 3: photos snapped at places. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `photos` (`id` TEXT NOT NULL, `ts` INTEGER NOT NULL, `lat` REAL, `lng` REAL, `placeId` TEXT, `city` TEXT, " +
                "`caption` TEXT, `file` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

/** 3 → 4: what the sync worker holds under each key. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_state` (`key` TEXT NOT NULL, `ts` INTEGER NOT NULL, `hash` TEXT, PRIMARY KEY(`key`))")
    }
}

val MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
