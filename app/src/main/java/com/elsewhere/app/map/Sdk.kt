package com.elsewhere.app.map

import android.content.Context
import com.elsewhere.app.BuildConfig
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Whether this phone can draw Google's native map: the build was given a Maps SDK key and Play services are
 * on the device. Decided once at start-up; until [init] runs (never in JVM tests) the answer is no, so tests and
 * goldens read the same whatever key the build was given.
 */
object Sdk {
    @Volatile var available: Boolean = false
        private set

    fun init(ctx: Context) {
        available = BuildConfig.HAS_MAPS_SDK &&
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS
    }
}
