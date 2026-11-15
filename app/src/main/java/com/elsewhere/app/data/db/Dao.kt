package com.elsewhere.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao interface VisitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(rows: List<VisitEntity>)
    @Query("SELECT * FROM visits ORDER BY start, id") suspend fun all(): List<VisitEntity>
    @Query("SELECT COUNT(*) FROM visits") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM visits WHERE source = 'device'") suspend fun countDevice(): Int
    @Query("DELETE FROM visits") suspend fun clear()
    @Query("DELETE FROM visits WHERE source = 'import'") suspend fun clearImported()
}

@Dao interface ActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(rows: List<ActivityEntity>)
    @Query("SELECT * FROM activities ORDER BY start, id") suspend fun all(): List<ActivityEntity>
    @Query("DELETE FROM activities") suspend fun clear()
    @Query("DELETE FROM activities WHERE source = 'import'") suspend fun clearImported()
}

@Dao interface PathDao {
    @Insert suspend fun insertAll(rows: List<PathEntity>)
    @Query("SELECT * FROM paths ORDER BY start, id") suspend fun all(): List<PathEntity>
    @Query("DELETE FROM paths") suspend fun clear()
    @Query("DELETE FROM paths WHERE source = 'import'") suspend fun clearImported()
}

@Dao interface RawFixDao {
    @Insert suspend fun insertAll(rows: List<RawFixEntity>)
    @Query("SELECT * FROM raw_fixes WHERE ts >= :ts ORDER BY ts") suspend fun since(ts: Long): List<RawFixEntity>
    @Query("SELECT * FROM raw_fixes ORDER BY ts DESC LIMIT 1") suspend fun latest(): RawFixEntity?
    @Query("SELECT COUNT(*) FROM raw_fixes") suspend fun countAll(): Int
    @Query("DELETE FROM raw_fixes WHERE ts < :ts") suspend fun pruneBefore(ts: Long)
    @Query("DELETE FROM raw_fixes") suspend fun clear()
}

@Dao interface PhotoDao {
    @Upsert suspend fun upsert(row: PhotoEntity)
    @Query("SELECT * FROM photos ORDER BY ts DESC") fun all(): Flow<List<PhotoEntity>>
    @Query("SELECT * FROM photos ORDER BY ts DESC") suspend fun list(): List<PhotoEntity>
    @Query("SELECT * FROM photos WHERE placeId = :placeId ORDER BY ts DESC") suspend fun forPlace(placeId: String): List<PhotoEntity>
    @Query("SELECT * FROM photos WHERE id = :id") suspend fun get(id: String): PhotoEntity?
    @Query("UPDATE photos SET caption = :caption WHERE id = :id") suspend fun caption(id: String, caption: String?)
    @Query("DELETE FROM photos WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM photos") suspend fun clear()
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
