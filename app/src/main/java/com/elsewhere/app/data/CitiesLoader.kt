package com.elsewhere.app.data

import android.content.Context
import com.elsewhere.app.ingest.Cities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads `assets/cities.json` once; later calls return the cached table. */
@Singleton
class CitiesLoader @Inject constructor(@ApplicationContext private val context: Context) {
    private val lock = Mutex()
    @Volatile private var cached: Cities? = null

    suspend fun get(): Cities = cached ?: lock.withLock {
        cached ?: withContext(Dispatchers.IO) {
            Cities.fromJson(context.assets.open("cities.json").bufferedReader().readText())
        }.also { cached = it }
    }
}
