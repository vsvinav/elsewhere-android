package com.elsewhere.app.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import com.elsewhere.app.sync.SyncApi
import com.elsewhere.app.sync.SyncBlob
import com.elsewhere.app.sync.SyncEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class AccountInfo(val id: String, val email: String, val created: Long, val hasVault: Boolean)

@Serializable
data class Login(val session: String, val account: AccountInfo)

/** A non-2xx from the account service: the status and its `error` word (or the raw body when there is none). */
class AccountException(val code: Int, val error: String) : IOException("$code $error")

/** The words the service uses, read back to the visitor the same way on both clients. */
fun Throwable.userMessage(): String = when {
    this is AccountException && error == "slow down" -> "Too many tries. Give it an hour."
    this is AccountException && error == "wrong code" -> "That code isn't right."
    this is AccountException && error == "expired" -> "That code expired. Send a new one."
    this is AccountException -> "Something went wrong. Try again."
    else -> "Couldn't reach the account service. Check your connection."
}

/** The account service's contract; the OkHttp client is real, tests hand the repository a fake. */
interface AccountApi {
    suspend fun start(email: String)
    suspend fun finish(email: String, code: String): Login
    /** A Google ID token for the same `{ session, account }`; 401 when Google would not vouch for it. */
    suspend fun google(idToken: String): Login
    suspend fun me(session: String): AccountInfo
    suspend fun logout(session: String)
    suspend fun putVault(session: String, body: VaultBody)
    suspend fun getVault(session: String): VaultBody?
    suspend fun deleteMe(session: String)
}

/** Emails are trimmed and lowercased before they leave, as the service normalises them. */
fun normalizeEmail(email: String): String = email.trim().lowercase()

class AccountClient(private val http: OkHttpClient, private val baseUrl: () -> String) : AccountApi, SyncApi {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun start(email: String) {
        call("POST", "/auth/start", """{"email":${quote(normalizeEmail(email))}}""")
    }

    override suspend fun finish(email: String, code: String): Login =
        json.decodeFromString(Login.serializer(), call("POST", "/auth/finish", """{"email":${quote(normalizeEmail(email))},"code":${quote(code)}}""")!!)

    override suspend fun google(idToken: String): Login =
        json.decodeFromString(Login.serializer(), call("POST", "/auth/google", """{"idToken":${quote(idToken)}}""")!!)

    override suspend fun me(session: String): AccountInfo = json.decodeFromString(AccountInfo.serializer(), call("GET", "/me", null, session)!!)
    override suspend fun logout(session: String) { call("DELETE", "/session", "", session) }
    override suspend fun putVault(session: String, body: VaultBody) { call("PUT", "/vault", body.encode(), session) }
    override suspend fun getVault(session: String): VaultBody? = call("GET", "/vault", null, session, allow404 = true)?.let { VaultBody.decode(it) }
    override suspend fun deleteMe(session: String) { call("DELETE", "/me", "", session) }

    override suspend fun list(session: String): List<SyncEntry> = json.decodeFromString(ListSerializer(SyncEntry.serializer()), call("GET", "/sync", null, session)!!)
    override suspend fun get(session: String, key: String): SyncBlob? = call("GET", "/sync/$key", null, session, allow404 = true)?.let { SyncBlob.decode(it) }
    override suspend fun put(session: String, key: String, blob: SyncBlob) { call("PUT", "/sync/$key", blob.encode(), session) }
    override suspend fun deleteAll(session: String) { call("DELETE", "/sync", "", session) }

    private fun quote(s: String) = Json.encodeToString(String.serializer(), s)

    private suspend fun call(method: String, path: String, body: String?, session: String? = null, allow404: Boolean = false): String? = withContext(Dispatchers.IO) {
        val base = baseUrl().trim().trimEnd('/')
        if (base.isBlank()) throw AccountException(0, "no account service")
        val req = Request.Builder().url(base + path)
            .apply { if (session != null) header("Authorization", "Bearer $session") }
            .method(method, if (body == null) null else body.toByteArray().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).await().use { r ->
            when {
                r.isSuccessful -> r.body?.string() ?: ""
                r.code == 404 && allow404 -> null
                else -> throw AccountException(r.code, errorWord(r.body?.string().orEmpty()) ?: r.message)
            }
        }
    }

    private fun errorWord(body: String): String? {
        val word = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
        return word ?: body.take(200).takeIf { it.isNotBlank() }
    }
}

/** OkHttp's callback as a suspension; shared with the sync and Drive clients. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { cont.resume(response) }
    })
    cont.invokeOnCancellation { cancel() }
}
