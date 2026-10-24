package com.example.treamaster.sync

import com.example.treamaster.BuildConfig
import com.example.treamaster.data.TreeDao
import com.example.treamaster.data.TreeMeasurement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

sealed interface UploadOutcome {
    data object Uploaded : UploadOutcome
    data object NotConfigured : UploadOutcome
    data class Failed(val reason: String) : UploadOutcome
}

sealed interface SyncResult {
    data class Done(val uploaded: Int) : SyncResult
    data object NotConfigured : SyncResult
    data class Failed(val uploaded: Int, val reason: String) : SyncResult
}

/**
 * Pushes unsynced rows to `tree_measurements` over Supabase's REST API (anon key, INSERT only).
 * Anonymous callers cannot UPDATE, so a re-sent row answers 409 (duplicate id); that means the cloud
 * already has it and is treated as success.
 */
class SupabaseUploader(
    private val dao: TreeDao,
    private val baseUrl: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
) {
    val isConfigured get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    suspend fun upload(m: TreeMeasurement): UploadOutcome = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext UploadOutcome.NotConfigured
        val code = try {
            post(m)
        } catch (e: Exception) {
            return@withContext UploadOutcome.Failed(e.message ?: e.javaClass.simpleName)
        }
        if (code in 200..299 || code == 409) {
            dao.markSynced(m.id, System.currentTimeMillis())
            UploadOutcome.Uploaded
        } else {
            UploadOutcome.Failed("HTTP $code")
        }
    }

    suspend fun syncAll(): SyncResult {
        if (!isConfigured) return SyncResult.NotConfigured
        var uploaded = 0
        for (m in dao.getUnsynced()) {
            when (val r = upload(m)) {
                UploadOutcome.Uploaded -> uploaded++
                UploadOutcome.NotConfigured -> return SyncResult.NotConfigured
                is UploadOutcome.Failed -> return SyncResult.Failed(uploaded, r.reason)
            }
        }
        return SyncResult.Done(uploaded)
    }

    private fun post(m: TreeMeasurement): Int {
        val conn = URL("${baseUrl.trimEnd('/')}/rest/v1/tree_measurements").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            conn.setRequestProperty("apikey", anonKey)
            conn.setRequestProperty("Authorization", "Bearer $anonKey")
            conn.setRequestProperty("Content-Type", "application/json")
            // Anonymous users cannot SELECT, so asking for the row back would fail.
            conn.setRequestProperty("Prefer", "return=minimal")
            conn.outputStream.use { it.write(toJson(m).toString().toByteArray(Charsets.UTF_8)) }
            return conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    private fun toJson(m: TreeMeasurement) = JSONObject().apply {
        put("id", m.uuid)
        put("tree_id", m.treeId ?: JSONObject.NULL)
        put("species", m.species ?: JSONObject.NULL)
        put("height_m", m.heightM ?: JSONObject.NULL)
        put("diameter_cm", m.diameterCm ?: JSONObject.NULL)
        put("distance_to_tree_m", m.distanceToTreeM ?: JSONObject.NULL)
        put("latitude", m.latitude ?: JSONObject.NULL)
        put("longitude", m.longitude ?: JSONObject.NULL)
        put("altitude_m", m.altitudeM ?: JSONObject.NULL)
        put("gps_accuracy_m", m.gpsAccuracyM ?: JSONObject.NULL)
        put("bearing_deg", m.bearingDeg ?: JSONObject.NULL)
        put("note", m.note)
        put("device_id", m.deviceId)
        put("created_at", Instant.ofEpochMilli(m.createdAtMs).toString())
    }
}
