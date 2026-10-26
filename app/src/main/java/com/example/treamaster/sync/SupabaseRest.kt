package com.example.treamaster.sync

import com.example.treamaster.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

class RestResponse(val code: Int, val body: String) {
    val isSuccess get() = code in 200..299

    /** Short reason for the user, using PostgREST's `message` when the body has one. */
    fun failureReason(): String {
        val message = Regex("\"message\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
        return if (message.isNullOrBlank()) "HTTP $code" else "HTTP $code $message"
    }
}

/** Minimal blocking client for Supabase's REST API (PostgREST) using the anon key. Call off the main thread. */
class SupabaseRest(
    private val baseUrl: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
) {
    val isConfigured get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    /** [path] is everything after `/rest/v1/`, including any query string. */
    fun request(method: String, path: String, body: String? = null, prefer: String? = null): RestResponse {
        val conn = URL("${baseUrl.trimEnd('/')}/rest/v1/$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("apikey", anonKey)
            conn.setRequestProperty("Authorization", "Bearer $anonKey")
            conn.setRequestProperty("Accept", "application/json")
            prefer?.let { conn.setRequestProperty("Prefer", it) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return RestResponse(code, text)
        } finally {
            conn.disconnect()
        }
    }
}
