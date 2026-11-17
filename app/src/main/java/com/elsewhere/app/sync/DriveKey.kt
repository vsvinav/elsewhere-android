package com.elsewhere.app.sync

import com.elsewhere.app.account.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.util.Base64

/** A non-2xx from Drive: the status and the first words of its body. */
class DriveException(val code: Int, detail: String) : IOException("drive $code $detail")

@Serializable private data class DriveFile(val id: String, val createdTime: String)
@Serializable private data class DriveList(val files: List<DriveFile> = emptyList())

/**
 * The one file in the user's Drive app folder, `elsewhere.key`: base64 of 32 random bytes. When two devices race and both
 * create one, everyone keeps the oldest by `createdTime` and deletes the rest, so they converge on a single key.
 */
class DriveKey(private val http: OkHttpClient, private val base: String = "https://www.googleapis.com") : DriveKeys {
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    override suspend fun fetchOrCreate(token: String): ByteArray {
        val files = list(token).sortedBy { it.createdTime }
        if (files.isEmpty()) return create(token)
        val bytes = Base64.getDecoder().decode(call(token, "GET", "/drive/v3/files/${files[0].id}?alt=media").trim())
        for (f in files.drop(1)) call(token, "DELETE", "/drive/v3/files/${f.id}")
        return bytes
    }

    override suspend fun delete(token: String) { for (f in list(token)) call(token, "DELETE", "/drive/v3/files/${f.id}") }

    private suspend fun list(token: String): List<DriveFile> =
        json.decodeFromString(DriveList.serializer(), call(token, "GET", "/drive/v3/files?spaces=appDataFolder&q=name%3D'$NAME'&fields=files(id,createdTime)")).files

    private suspend fun create(token: String): ByteArray {
        val key = ByteArray(32).also { random.nextBytes(it) }
        val body = "--$BOUNDARY\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
            """{"name":"$NAME","parents":["appDataFolder"]}""" +
            "\r\n--$BOUNDARY\r\nContent-Type: application/octet-stream\r\n\r\n" + Base64.getEncoder().encodeToString(key) + "\r\n--$BOUNDARY--"
        call(token, "POST", "/upload/drive/v3/files?uploadType=multipart", body, "multipart/related; boundary=$BOUNDARY")
        return key
    }

    private suspend fun call(token: String, method: String, path: String, body: String? = null, type: String? = null): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(base + path).header("Authorization", "Bearer $token")
            .method(method, body?.toByteArray()?.toRequestBody(type!!.toMediaType())).build()
        http.newCall(req).await().use { r ->
            if (!r.isSuccessful) throw DriveException(r.code, r.body?.string().orEmpty().take(200))
            r.body?.string().orEmpty()
        }
    }

    private companion object {
        const val NAME = "elsewhere.key"
        const val BOUNDARY = "elsewhere_key_boundary"
    }
}
