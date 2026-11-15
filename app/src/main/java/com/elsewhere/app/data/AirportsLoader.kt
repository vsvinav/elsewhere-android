package com.elsewhere.app.data

import android.content.Context
import com.elsewhere.app.analytics.Airport
import com.elsewhere.app.analytics.Airports
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads `assets/airports.json` (the web's AIRPORTS table) once; later calls return the cached list. */
@Singleton
class AirportsLoader @Inject constructor(@ApplicationContext private val context: Context, private val readyState: ReadyState = ReadyState()) {
    private val lock = Mutex()
    @Volatile private var cached: List<Airport>? = null

    suspend fun get(): List<Airport> = cached ?: lock.withLock {
        cached ?: readyState.drawing { withContext(Dispatchers.IO) {
            Airports.fromJson(context.assets.open("airports.json").bufferedReader().readText())
        } }.also { cached = it }
    }
}
