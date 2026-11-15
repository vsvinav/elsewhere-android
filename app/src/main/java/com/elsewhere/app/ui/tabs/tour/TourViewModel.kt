package com.elsewhere.app.ui.tabs.tour

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.app.analytics.HomeFacts
import com.elsewhere.app.analytics.Tour
import com.elsewhere.app.analytics.bedStrip
import com.elsewhere.app.analytics.homeFacts
import com.elsewhere.app.analytics.placeName
import com.elsewhere.app.analytics.tour
import com.elsewhere.app.data.FeatureCollection
import com.elsewhere.app.data.LandSource
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.RecordStore
import com.elsewhere.app.data.Units
import com.elsewhere.app.ui.shell.RangeStore
import com.elsewhere.app.ui.world.NightsUi
import com.elsewhere.app.ui.world.TrailLayout
import com.elsewhere.app.ui.world.nightsUi
import com.elsewhere.app.ui.world.trailLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TourUi(
    val name: String, val units: Units, val facts: HomeFacts, val tour: Tour, val trail: TrailLayout,
    val nights: NightsUi?, val actPlace: Place?, val actName: String?, val actSub: String?,
    val hasWork: Boolean, val homeCity: String?, val record: Record, val named: Int, val photos: Int,
) {
    val yearSpan: String get() = tour.years.let { (a, b) -> if (a == b) "$a" else "$a–$b" }
}

/** Everything the Tour poster prints, derived once per record, range, profile and land. */
fun tourUi(data: Record, raw: Record, profile: Profile?, land: FeatureCollection?): TourUi {
    val f = homeFacts(data)
    val t = tour(data)
    val act = t.headline
    val actPlace = act?.let { data.places[it.placeId] }
    val name = profile?.name ?: ""
    return TourUi(
        name = name, units = profile?.units ?: Units.KM, facts = f, tour = t,
        trail = trailLayout(data, t.stops, land),
        nights = nightsUi(bedStrip(raw, data.meta.range), data.meta.range, name),
        actPlace = actPlace, actName = act?.let { placeName(actPlace, it.placeId) },
        actSub = act?.let { listOfNotNull(actPlace?.address, it.country).joinToString(" · ").ifEmpty { null } },
        hasWork = !profile?.works.isNullOrEmpty(), homeCity = profile?.homes?.lastOrNull()?.city,
        record = data, named = data.places.values.count { it.name != null }, photos = data.places.values.count { it.photo != null },
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TourViewModel @Inject constructor(range: RangeStore, store: RecordStore, landSource: LandSource) : ViewModel() {
    private data class In(val data: Record, val raw: Record, val profile: Profile?, val land: FeatureCollection?)

    val ui: StateFlow<TourUi?> = combine(range.filtered, range.raw, store.profile, flow { emit(null); emit(landSource.get()) }) { data, raw, prof, l ->
        if (data == null || raw == null) null else In(data, raw, prof, l)
    }.mapLatest { it?.run { tourUi(data, raw, profile, land) } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
