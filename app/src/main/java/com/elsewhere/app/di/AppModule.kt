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
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.SettingsStore
import com.elsewhere.app.data.db.ElsewhereDb
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun db(@ApplicationContext ctx: Context): ElsewhereDb =
        Room.databaseBuilder(ctx, ElsewhereDb::class.java, "elsewhere.db").fallbackToDestructiveMigration().build()

    @Provides @Singleton fun dataStore(@ApplicationContext ctx: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { ctx.preferencesDataStoreFile("settings") }

    @Provides @Singleton fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides fun recordSource(repo: RecordRepository): RecordSource = repo
    @Provides fun recordStore(repo: RecordRepository): RecordStore = repo
    @Provides fun mapsSettings(store: SettingsStore): MapsSettings = store

    @Provides fun visits(db: ElsewhereDb) = db.visits()
    @Provides fun activities(db: ElsewhereDb) = db.activities()
    @Provides fun paths(db: ElsewhereDb) = db.paths()
    @Provides fun memories(db: ElsewhereDb) = db.memories()
    @Provides fun frequentPlaces(db: ElsewhereDb) = db.frequentPlaces()
    @Provides fun placeEnrichment(db: ElsewhereDb) = db.placeEnrichment()
    @Provides fun importMeta(db: ElsewhereDb) = db.importMeta()
}
