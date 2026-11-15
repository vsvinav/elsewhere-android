package com.elsewhere.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.elsewhere.app.data.RecordRepository
import com.elsewhere.app.data.RecordSource
import com.elsewhere.app.data.RecordStore
import com.elsewhere.app.data.LandLoader
import com.elsewhere.app.data.LandSource
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.SettingsStore
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.data.db.MIGRATIONS
import com.elsewhere.app.record.AndroidPermissionGate
import com.elsewhere.app.record.PermissionGate
import com.elsewhere.app.record.RecordControl
import com.elsewhere.app.record.AndroidServiceLink
import com.elsewhere.app.record.RecordController
import com.elsewhere.app.record.ServiceLink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import com.elsewhere.app.data.GoogleTiles
import com.elsewhere.app.data.TileService
import com.elsewhere.app.ingest.AddressBook
import com.elsewhere.app.ingest.CostMeter
import com.elsewhere.app.ingest.EnrichmentStore
import com.elsewhere.app.ingest.Geocoder
import com.elsewhere.app.ingest.PlaceLookups
import com.elsewhere.app.ingest.Nominatim
import com.elsewhere.app.ingest.PlaceNamer
import com.elsewhere.app.ingest.KeyCheck
import com.elsewhere.app.ingest.KeyChecker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun db(@ApplicationContext ctx: Context): ElsewhereDb =
        Room.databaseBuilder(ctx, ElsewhereDb::class.java, "elsewhere.db").addMigrations(*MIGRATIONS).fallbackToDestructiveMigration().build()

    @Provides @Singleton fun dataStore(@ApplicationContext ctx: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { ctx.preferencesDataStoreFile("settings") }

    @Provides @Singleton fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton fun http(): OkHttpClient = OkHttpClient()
    @Provides fun keyChecker(check: KeyCheck): KeyChecker = check

    @Provides fun recordSource(repo: RecordRepository): RecordSource = repo
    @Provides fun recordStore(repo: RecordRepository): RecordStore = repo
    @Provides fun mapsSettings(store: SettingsStore): MapsSettings = store
    @Provides fun costMeter(store: SettingsStore): CostMeter = store
    @Provides fun addressBook(store: SettingsStore): AddressBook = store
    @Provides fun enrichmentStore(repo: RecordRepository): EnrichmentStore = repo
    @Provides fun geocoder(n: Nominatim): Geocoder = n
    @Provides fun placeNamer(l: PlaceLookups): PlaceNamer = l
    @Provides fun tileService(t: GoogleTiles): TileService = t
    @Provides fun landSource(loader: LandLoader): LandSource = loader
    @Provides fun permissionGate(gate: AndroidPermissionGate): PermissionGate = gate
    @Provides fun recordControl(c: RecordController): RecordControl = c
    @Provides fun serviceLink(l: AndroidServiceLink): ServiceLink = l

    @Provides fun visits(db: ElsewhereDb) = db.visits()
    @Provides fun activities(db: ElsewhereDb) = db.activities()
    @Provides fun paths(db: ElsewhereDb) = db.paths()
    @Provides fun rawFixes(db: ElsewhereDb) = db.rawFixes()
    @Provides fun memories(db: ElsewhereDb) = db.memories()
    @Provides fun frequentPlaces(db: ElsewhereDb) = db.frequentPlaces()
    @Provides fun placeEnrichment(db: ElsewhereDb) = db.placeEnrichment()
    @Provides fun importMeta(db: ElsewhereDb) = db.importMeta()
}
