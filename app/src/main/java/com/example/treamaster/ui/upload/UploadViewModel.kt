package com.example.treamaster.ui.upload

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.treamaster.BuildConfig
import com.example.treamaster.R
import com.example.treamaster.data.Batch
import com.example.treamaster.data.DeviceId
import com.example.treamaster.data.Region
import com.example.treamaster.data.RegionStore
import com.example.treamaster.data.TreeDatabase
import com.example.treamaster.data.TreeId
import com.example.treamaster.data.TreeMeasurement
import com.example.treamaster.sync.SupabaseUploader
import com.example.treamaster.sync.SyncResult
import com.example.treamaster.sync.UploadOutcome
import com.example.treamaster.ui.message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UploadViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = TreeDatabase.get(app).dao()
    private val uploader = SupabaseUploader(dao)
    private val regionStore = RegionStore.get(app)
    val regions: StateFlow<List<Region>> = regionStore.regions

    /** One entry per finished scan. */
    val batches: StateFlow<List<Batch>> =
        dao.observeBatches().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeBatch(batchId: String): Flow<List<TreeMeasurement>> = dao.observeBatch(batchId)

    var uploading by mutableStateOf(false)
        private set

    /** One-shot text for the snackbar; the screen calls [messageShown] after displaying it. */
    var message by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            // Test trees can overwrite real ones on the website, so release builds carry none.
            val wanted = if (BuildConfig.DEBUG) TreeMeasurement.SAMPLE_COUNT else 0
            if (dao.sampleCount() != wanted) {
                dao.deleteSamples()
                if (wanted > 0) dao.insertAll(TreeMeasurement.samples(DeviceId.get(app)))
            }
        }
        refreshRegions()
    }

    fun refreshRegions() {
        viewModelScope.launch { regionStore.refresh() }
    }

    /** Numbers the test trees [regionCode]-1 … -20 in their fixed order, ready for "全部上傳". */
    fun assignSamplesToRegion(regionCode: String) {
        val region = regions.value.find { it.code == regionCode } ?: return
        viewModelScope.launch {
            val samples = dao.getSamples()
            samples.forEachIndexed { i, m ->
                dao.updateTreeId(m.id, if (i < region.slotCount) "${region.code}-${i + 1}" else null)
            }
            message = str(R.string.msg_samples_assigned, region.code, minOf(samples.size, region.slotCount))
        }
    }

    /** Gives [row] a number from region + slot; returns the problem's string resource, or null once saved. */
    fun assignTreeId(row: TreeMeasurement, regionCode: String?, slot: String): Int? {
        val treeId = when (val built = TreeId.build(regions.value.find { it.code == regionCode }, slot)) {
            TreeId.Built.Blank -> null
            is TreeId.Built.Ok -> built.id
            is TreeId.Built.Invalid -> return built.problem.message()
        }
        if (treeId == row.treeId) return null
        viewModelScope.launch {
            dao.updateTreeId(row.id, treeId)
            message = str(R.string.msg_tree_id_saved)
        }
        return null
    }

    fun observe(id: Long): Flow<TreeMeasurement?> = dao.observe(id)

    fun messageShown() {
        message = null
    }

    fun upload(row: TreeMeasurement) = runUpload {
        message = when (val r = uploader.upload(row)) {
            UploadOutcome.Uploaded -> str(R.string.msg_uploaded)
            UploadOutcome.NotConfigured -> str(R.string.msg_not_configured)
            UploadOutcome.NoTreeId -> str(R.string.msg_no_tree_id)
            is UploadOutcome.Failed -> str(R.string.msg_upload_failed, r.reason)
        }
    }

    /** Removes a whole scan from the phone. The test scan is protected. */
    fun deleteBatch(batchId: String, onDone: () -> Unit) {
        if (batchId == TreeMeasurement.SAMPLE_BATCH) return
        viewModelScope.launch {
            dao.deleteBatch(batchId)
            onDone()
        }
    }

    fun uploadBatch(batchId: String) = runUpload {
        message = when (val r = uploader.syncBatch(batchId)) {
            is SyncResult.Done ->
                if (r.skipped == 0) str(R.string.msg_uploaded_n, r.uploaded)
                else str(R.string.msg_uploaded_n_skipped, r.uploaded, r.skipped)
            SyncResult.NotConfigured -> str(R.string.msg_not_configured)
            is SyncResult.Failed ->
                if (r.uploaded == 0) str(R.string.msg_upload_failed, r.reason)
                else str(R.string.msg_upload_failed_partial, r.uploaded, r.reason)
        }
    }

    fun delete(row: TreeMeasurement, onDone: () -> Unit) {
        if (row.isSample) return
        viewModelScope.launch {
            dao.delete(row.id)
            onDone()
        }
    }

    private fun runUpload(block: suspend () -> Unit) {
        if (uploading) return
        uploading = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                uploading = false
            }
        }
    }

    private fun str(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)
}
