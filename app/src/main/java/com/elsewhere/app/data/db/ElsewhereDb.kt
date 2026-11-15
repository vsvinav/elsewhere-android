package com.elsewhere.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [VisitEntity::class, ActivityEntity::class, PathEntity::class, MemoryEntity::class,
        FrequentPlaceEntity::class, PlaceEnrichmentEntity::class, ImportMetaEntity::class],
    version = 1, exportSchema = false,
)
abstract class ElsewhereDb : RoomDatabase() {
    abstract fun visits(): VisitDao
    abstract fun activities(): ActivityDao
    abstract fun paths(): PathDao
    abstract fun memories(): MemoryDao
    abstract fun frequentPlaces(): FrequentPlaceDao
    abstract fun placeEnrichment(): PlaceEnrichmentDao
    abstract fun importMeta(): ImportMetaDao
}
