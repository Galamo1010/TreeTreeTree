package com.example.treamaster.sync

import com.example.treamaster.data.TreeDao
import com.example.treamaster.data.TreeMeasurement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId

sealed interface UploadOutcome {
    data object Uploaded : UploadOutcome
    data object NotConfigured : UploadOutcome
    /** The website files trees by number (region-slot), so an unnumbered row has nowhere to go. */
    data object NoTreeId : UploadOutcome
    data class Failed(val reason: String) : UploadOutcome
}

sealed interface SyncResult {
    /** [skipped] rows had no tree number and were left on the phone. */
    data class Done(val uploaded: Int, val skipped: Int) : SyncResult
    data object NotConfigured : SyncResult
    data class Failed(val uploaded: Int, val reason: String) : SyncResult
}

/**
 * Writes measurements into the website's `treevalue` table: one row per tree number (e.g. "A-3"),
 * height and diameter in cm. Re-uploading the same number replaces the website's values with this
 * measurement; fields the phone does not have (e.g. a skipped diameter) keep their website value.
 */
class SupabaseUploader(
    private val dao: TreeDao,
    private val rest: SupabaseRest = SupabaseRest(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun upload(m: TreeMeasurement): UploadOutcome = withContext(Dispatchers.IO) {
        if (!rest.isConfigured) return@withContext UploadOutcome.NotConfigured
        if (m.treeId == null) return@withContext UploadOutcome.NoTreeId
        val response = try {
            rest.request(
                method = "POST",
                path = "treevalue?on_conflict=tree_id",
                body = JSONArray().put(toTreeValue(m, zone)).toString(),
                prefer = "resolution=merge-duplicates,return=minimal",
            )
        } catch (e: Exception) {
            return@withContext UploadOutcome.Failed(e.message ?: e.javaClass.simpleName)
        }
        if (response.isSuccess) {
            dao.markSynced(m.id, System.currentTimeMillis())
            UploadOutcome.Uploaded
        } else {
            UploadOutcome.Failed(response.failureReason())
        }
    }

    /** Uploads every not-yet-uploaded tree of one scan. */
    suspend fun syncBatch(batchId: String): SyncResult {
        if (!rest.isConfigured) return SyncResult.NotConfigured
        var uploaded = 0
        var skipped = 0
        for (m in dao.getUnsynced(batchId)) {
            when (val r = upload(m)) {
                UploadOutcome.Uploaded -> uploaded++
                // Samples without a region are simply not in use, not something the user forgot.
                UploadOutcome.NoTreeId -> if (!m.isSample) skipped++
                UploadOutcome.NotConfigured -> return SyncResult.NotConfigured
                is UploadOutcome.Failed -> return SyncResult.Failed(uploaded, r.reason)
            }
        }
        return SyncResult.Done(uploaded, skipped)
    }

    companion object {
        /** The `treevalue` row for [m]. Null measurements are left out so the website keeps its value. */
        fun toTreeValue(m: TreeMeasurement, zone: ZoneId): JSONObject = JSONObject().apply {
            put("tree_id", requireNotNull(m.treeId))
            m.heightM?.let { put("height", cm(it * 100)) }
            m.diameterCm?.let { put("diameter", cm(it)) }
            m.species?.let { put("species", it) }
            put("date", Instant.ofEpochMilli(m.createdAtMs).atZone(zone).toLocalDate().toString())
        }

        /** The website stores cm with two decimals. */
        private fun cm(v: Double) = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP)
    }
}
