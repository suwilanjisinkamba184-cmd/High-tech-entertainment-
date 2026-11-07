package com.hightechentertainment.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import android.content.Context
import android.net.Uri
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Authenticated PostgREST transport. RLS remains the authorization boundary. */
class SupabaseRestClient(
    private val baseUrl: String = SupabaseConfig.BASE_URL,
    private val publishableKey: String = SupabaseConfig.PUBLISHABLE_KEY,
    private val accessToken: () -> String? = { null },
) {
    data class Response(val statusCode: Int, val body: String) {
        val isSuccessful: Boolean get() = statusCode in 200..299
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun get(table: String, query: Map<String, String> = emptyMap(), select: String = "*"): Response =
        request("GET", "/rest/v1/${segment(table)}", query + ("select" to select))

    suspend fun post(table: String, jsonBody: String, prefer: String = "return=representation"): Response =
        request("POST", "/rest/v1/${segment(table)}", body = jsonBody,
            headers = mapOf("Content-Type" to "application/json", "Prefer" to prefer))

    suspend fun request(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Response = withContext(Dispatchers.IO) {
        require(path.startsWith("/") && !path.startsWith("//")) { "Path must be relative to the Supabase project." }
        require(!path.contains("://")) { "Absolute URLs are not accepted." }
        val queryString = query.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val endpoint = baseUrl.trimEnd('/') + path + if (queryString.isEmpty()) "" else "?$queryString"
        val requestBuilder = Request.Builder().url(endpoint)
            .header("apikey", publishableKey)
            .header("Accept", "application/json")
        accessToken()?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }
        headers.forEach { (name, value) -> requestBuilder.header(name, value) }
        val json = body?.toRequestBody("application/json; charset=utf-8".toMediaType())
        when (method.uppercase()) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(json ?: ByteArray(0).toRequestBody(null))
            "PUT" -> requestBuilder.put(json ?: ByteArray(0).toRequestBody(null))
            "PATCH" -> requestBuilder.patch(json ?: ByteArray(0).toRequestBody(null))
            "DELETE" -> if (json == null) requestBuilder.delete() else requestBuilder.delete(json)
            else -> error("Unsupported HTTP method.")
        }
        try {
            http.newCall(requestBuilder.build()).execute().use { response ->
                Response(response.code, response.body?.string().orEmpty())
            }
        } catch (error: IOException) {
            throw IOException("Unable to reach High-Tech Entertainment services. Check your connection and retry.", error)
        }
    }

    suspend fun uploadStorageObject(
        bucket: String,
        objectPath: String,
        bytes: ByteArray,
        contentType: String
    ): Response = withContext(Dispatchers.IO) {
        require(segment(bucket) == bucket)
        require(objectPath.isNotBlank() && !objectPath.startsWith("/") && !objectPath.contains(".."))
        val encodedPath = objectPath.split("/").joinToString("/") { encode(it).replace("+", "%20") }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/storage/v1/object/$bucket/$encodedPath")
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer ${accessToken()?.takeIf { it.isNotBlank() } ?: publishableKey}")
            .header("Content-Type", contentType)
            .header("x-upsert", "false")
            .post(bytes.toRequestBody(contentType.toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            Response(response.code, response.body?.string().orEmpty())
        }
    }

    suspend fun uploadStorageUri(
        context: Context,
        bucket: String,
        objectPath: String,
        uri: Uri,
        contentType: String,
        maxBytes: Long
    ): Response = withContext(Dispatchers.IO) {
        require(segment(bucket) == bucket)
        require(objectPath.isNotBlank() && !objectPath.startsWith("/") && !objectPath.contains(".."))
        require(contentType.startsWith("video/"))
        val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
        val length = descriptor?.use { it.length } ?: -1L
        require(length <= 0L || length <= maxBytes) { "Selected video exceeds the upload size limit." }
        val encodedPath = objectPath.split("/").joinToString("/") { encode(it).replace("+", "%20") }
        val requestBody = object : RequestBody() {
            override fun contentType() = contentType.toMediaType()
            override fun contentLength() = length
            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= maxBytes) { "Selected video exceeds the upload size limit." }
                        sink.write(buffer, 0, count)
                    }
                } ?: error("Could not read the selected video file.")
            }
        }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/storage/v1/object/$bucket/$encodedPath")
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer ${accessToken()?.takeIf { it.isNotBlank() } ?: publishableKey}")
            .header("Content-Type", contentType)
            .header("x-upsert", "false")
            .post(requestBody)
            .build()
        http.newCall(request).execute().use { response ->
            Response(response.code, response.body?.string().orEmpty())
        }
    }

    fun publicStorageUrl(bucket: String, objectPath: String): String {
        require(segment(bucket) == bucket)
        val encodedPath = objectPath.split("/").joinToString("/") { encode(it).replace("+", "%20") }
        return baseUrl.trimEnd('/') + "/storage/v1/object/public/$bucket/$encodedPath"
    }

    private fun segment(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9_]+"))) { "Invalid API resource name." }
        return value
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
