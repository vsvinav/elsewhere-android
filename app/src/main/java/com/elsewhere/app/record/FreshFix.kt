package com.elsewhere.app.record

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import com.elsewhere.app.ingest.record.Fix
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

/** One high-accuracy fix asked for right now, or null when the phone has none to give within ten seconds. */
fun interface FreshFix {
    suspend fun get(): Fix?

    companion object { const val TIMEOUT_MS = 10_000L }
}

class FusedFreshFix @Inject constructor(@ApplicationContext private val ctx: Context, private val gate: PermissionGate) : FreshFix {
    @SuppressLint("MissingPermission")
    override suspend fun get(): Fix? {
        if (!gate.granted(Manifest.permission.ACCESS_FINE_LOCATION)) return null
        val fused = LocationServices.getFusedLocationProviderClient(ctx)
        val cancel = CancellationTokenSource()
        return withTimeoutOrNull(FreshFix.TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token)
                    .addOnSuccessListener { l -> if (cont.isActive) cont.resume(l?.let { Fix(it.time / 1000, it.latitude, it.longitude, it.accuracy, if (it.hasSpeed()) it.speed else null) }) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { cancel.cancel() }
            }
        }.also { if (it == null) cancel.cancel() }
    }
}
