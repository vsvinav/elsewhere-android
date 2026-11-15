package com.elsewhere.app

import com.elsewhere.app.data.Counters
import com.elsewhere.app.data.FeatureCollection
import com.elsewhere.app.data.ImportReport
import com.elsewhere.app.data.LandSource
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.RecordSource
import com.elsewhere.app.data.RecordStore
import com.elsewhere.app.data.Settings
import com.elsewhere.app.data.Place
import com.elsewhere.app.ingest.DetailsSource
import com.elsewhere.app.ingest.EnrichState
import com.elsewhere.app.ingest.Enrichment
import com.elsewhere.app.ingest.Geocoder
import com.elsewhere.app.ingest.MemoryCostMeter
import com.elsewhere.app.ingest.MemoryEnrichmentStore
import com.elsewhere.app.ingest.PlaceLookups
import kotlinx.coroutines.CoroutineScope
import com.elsewhere.app.ingest.KeyChecker
import com.elsewhere.app.ingest.PlaceNamer
import com.elsewhere.app.ingest.KeyReport
import com.elsewhere.app.ingest.Probe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.InputStream

/** In-memory stand-ins for the data seams the view models read. */
object Fakes {
    class Store(private val rec: Record) : RecordStore {
        override val ready = MutableStateFlow(true)
        override val hasImport = MutableStateFlow(false)
        override val profile = MutableStateFlow<Profile?>(null)
        var saved: Profile? = null
        var resets = 0
        override suspend fun import(open: () -> InputStream, fileName: String, onProgress: (Float) -> Unit): ImportReport {
            open().use { it.readBytes() }; onProgress(1f); hasImport.value = true
            return ImportReport(100, 98, mapOf("bad_json" to 2))
        }
        override suspend fun provisionalRecord(): Record = rec
        override suspend fun saveProfile(p: Profile) { saved = p; profile.value = p }
        override suspend fun reset() { resets++; hasImport.value = false; profile.value = null }
    }

    class Maps : MapsSettings {
        val cur = MutableStateFlow(Settings())
        override val settings: Flow<Settings> = cur
        override val counters: Flow<Counters> = MutableStateFlow(Counters())
        override suspend fun saveSettings(s: Settings) { cur.value = s }
    }

    class Checker(var report: KeyReport = KeyReport(Probe.Ok, Probe.Ok)) : KeyChecker {
        val asked = ArrayList<String>()
        var onRun: () -> Unit = {}
        override suspend fun run(key: String): KeyReport { asked += key; onRun(); return report }
    }

    /** Hands back a scripted name per leading place id and records what it was asked. */
    class Namer(val names: Map<String, String> = emptyMap()) : PlaceNamer {
        val asked = ArrayList<List<String>>()
        override val progress = MutableStateFlow<com.elsewhere.app.ingest.Progress?>(null)
        override suspend fun name(ids: List<String>, all: Map<String, Place>, key: String): String? { asked += ids; return names[ids.first()] }
    }

    class Geo(val addresses: Map<Pair<Double, Double>, String> = emptyMap()) : Geocoder {
        val asked = ArrayList<Pair<Double, Double>>()
        override suspend fun address(lat: Double, lng: Double): String? { asked += lat to lng; return addresses[lat to lng] }
    }

    class Source(initial: Record? = null) : RecordSource {
        override val record = MutableStateFlow(initial)
    }

    /** Place lookups that never go out: a key-less pool over an empty store. */
    fun lookups(maps: Maps, scope: CoroutineScope) = PlaceLookups(
        object : DetailsSource { override suspend fun details(id: String, key: String) = Enrichment.NONE },
        MemoryEnrichmentStore(), MemoryCostMeter(), EnrichState(), scope, maps.settings,
    )

    object Land : LandSource {
        override suspend fun get() = FeatureCollection(emptyList())
    }
}
