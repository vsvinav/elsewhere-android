package com.elsewhere.app.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A Drive `appdata` access token for the signed-in Google account; the real one is Play's AuthorizationClient, tests hand in a fake. */
interface DriveAuth {
    /** A short-lived token, kept in memory only, or [DriveAuthFailed]; `activity` must be the foreground Activity, consent may be drawn over it. */
    suspend fun accessToken(activity: Context): String
}

/** Consent was refused, the sheet dismissed, or Play could not authorise. */
class DriveAuthFailed(cause: Throwable? = null) : Exception("Drive access didn't finish", cause)

/**
 * Play's AuthorizationClient over the one scope. The first time, Google asks for consent through a PendingIntent, which has to
 * go through an Activity's result launcher: [attach] registers it in `onCreate`, before the Activity is started.
 */
@Singleton
class PlayDriveAuth @Inject constructor() : DriveAuth {
    private var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var pending: CompletableDeferred<Intent?>? = null

    fun attach(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(StartIntentSenderForResult()) { r ->
            pending?.complete(if (r.resultCode == Activity.RESULT_OK) r.data else null)
        }
    }

    override suspend fun accessToken(activity: Context): String {
        val client = Identity.getAuthorizationClient(activity)
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
        val result = try { client.authorize(request).await() } catch (e: Exception) { throw DriveAuthFailed(e) }
        if (!result.hasResolution()) return result.accessToken ?: throw DriveAuthFailed()
        val l = launcher ?: throw DriveAuthFailed()
        val sender = result.pendingIntent?.intentSender ?: throw DriveAuthFailed()
        val d = CompletableDeferred<Intent?>()
        pending = d
        l.launch(IntentSenderRequest.Builder(sender).build())
        val data = d.await() ?: throw DriveAuthFailed()
        return try { client.getAuthorizationResultFromIntent(data).accessToken } catch (e: Exception) { throw DriveAuthFailed(e) } ?: throw DriveAuthFailed()
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

    companion object { const val SCOPE = "https://www.googleapis.com/auth/drive.appdata" }
}
