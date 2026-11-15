package com.elsewhere.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao interface VisitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(rows: List<VisitEntity>)
    @Query("SELECT * FROM visits ORDER BY id") suspend fun all(): List<VisitEntity>
    @Query("SELECT COUNT(*) FROM visits") suspend fun count(): Int
    @Query("DELETE FROM visits") suspend fun clear()
}

@Dao interface ActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(rows: List<ActivityEntity>)
    @Query("SELECT * FROM activities ORDER BY id") suspend fun all(): List<ActivityEntity>
    @Query("DELETE FROM activities") suspend fun clear()
}

@Dao interface PathDao {
    @Insert suspend fun insertAll(rows: List<PathEntity>)
    @Query("SELECT * FROM paths ORDER BY id") suspend fun all(): List<PathEntity>
    @Query("DELETE FROM paths") suspend fun clear()
}

@Dao interface MemoryDao {
    @Insert suspend fun insertAll(rows: List<MemoryEntity>)
    @Query("SELECT * FROM memories ORDER BY id") suspend fun all(): List<MemoryEntity>
    @Query("DELETE FROM memories") suspend fun clear()
}

@Dao interface FrequentPlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(rows: List<FrequentPlaceEntity>)
    @Query("SELECT * FROM frequent_places") suspend fun all(): List<FrequentPlaceEntity>
    @Query("DELETE FROM frequent_places") suspend fun clear()
}

@Dao interface PlaceEnrichmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(rows: List<PlaceEnrichmentEntity>)
    @Upsert suspend fun upsert(row: PlaceEnrichmentEntity)
    @Query("SELECT * FROM place_enrichment") suspend fun all(): List<PlaceEnrichmentEntity>
    @Query("DELETE FROM place_enrichment") suspend fun clear()
}

@Dao interface ImportMetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(rows: List<ImportMetaEntity>)
    @Query("SELECT * FROM import_meta WHERE id = 1") suspend fun get(): ImportMetaEntity?
    @Query("SELECT * FROM import_meta") suspend fun all(): List<ImportMetaEntity>
    @Query("DELETE FROM import_meta") suspend fun clear()
}
