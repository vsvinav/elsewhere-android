package com.elsewhere.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** The Google account sheet; the real one is Credential Manager, tests hand the view model a fake. */
interface GoogleAuth {
    /** An ID token for the account service, or [GoogleSignInFailed]; `activity` must be the foreground Activity, the sheet is drawn over it. */
    suspend fun idToken(activity: Context): String
}

/** The sheet was dismissed, found no account, or returned something that is not a Google ID token. */
class GoogleSignInFailed(cause: Throwable? = null) : Exception("Google sign-in didn't finish", cause)

class CredentialGoogleAuth(private val serverClientId: String) : GoogleAuth {
    override suspend fun idToken(activity: Context): String {
        val option = GetGoogleIdOption.Builder().setServerClientId(serverClientId).setFilterByAuthorizedAccounts(false).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = try { CredentialManager.create(activity).getCredential(activity, request).credential } catch (e: GetCredentialException) { throw GoogleSignInFailed(e) }
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) throw GoogleSignInFailed()
        return try { GoogleIdTokenCredential.createFrom(credential.data).idToken } catch (e: GoogleIdTokenParsingException) { throw GoogleSignInFailed(e) }
    }
}
