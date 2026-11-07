package com.hightechentertainment.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Small dependency-free PostgREST/Auth transport used while feature repositories are migrated.
 * This class deliberately does not make authorization decisions: RLS remains authoritative.
 */
class SupabaseRestClient(
    private val baseUrl: String = SupabaseConfig.BASE_URL,
    private val publishableKey: String = SupabaseConfig.PUBLISHABLE_KEY,
    private val accessToken: () -> String? = { null },
) {
    data class Response(val statusCode: Int, val body: String) {
        val isSuccessful: Boolean get() = statusCode in 200..299
    }

    suspend fun get(
        table: String,
        query: Map<String, String> = emptyMap(),
        select: String = "*",
    ): Response = request(
        method = "GET",
        path = "/rest/v1/${segment(table)}",
        query = query + ("select" to select),
    )

    suspend fun post(
        table: String,
        jsonBody: String,
        prefer: String = "return=representation",
    ): Response = request(
        method = "POST",
        path = "/rest/v1/${segment(table)}",
        body = jsonBody,
        headers = mapOf("Content-Type" to "application/json", "Prefer" to prefer),
    )

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
        val endpoint = baseUrl.trimEnd('/') + path +
            if (queryString.isEmpty()) "" else "?$queryString"

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Accept", "application/json")
            accessToken()?.takeIf { it.isNotBlank() }?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        try {
            if (body != null) {
                connection.outputStream.use { stream ->
                    stream.write(body.toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            Response(status, responseBody)
        } catch (error: IOException) {
            throw IOException("Unable to reach High-Tech Entertainment services. Check your connection and retry.", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun segment(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9_]+"))) { "Invalid API resource name." }
        return value
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
