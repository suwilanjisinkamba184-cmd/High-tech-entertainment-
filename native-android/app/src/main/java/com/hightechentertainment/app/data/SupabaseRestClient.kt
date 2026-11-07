package com.hightechentertainment.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    private fun segment(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9_]+"))) { "Invalid API resource name." }
        return value
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
