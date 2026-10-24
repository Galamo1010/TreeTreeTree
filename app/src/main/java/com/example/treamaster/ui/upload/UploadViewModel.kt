package com.example.treamaster.ui.upload

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.treamaster.R
import com.example.treamaster.data.DeviceId
import com.example.treamaster.data.TreeDatabase
import com.example.treamaster.data.TreeMeasurement
import com.example.treamaster.sync.SupabaseUploader
import com.example.treamaster.sync.SyncResult
import com.example.treamaster.sync.UploadOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UploadViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = TreeDatabase.get(app).dao()
    private val uploader = SupabaseUploader(dao)

    val rows: StateFlow<List<TreeMeasurement>> =
        dao.observeUploadList().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var uploading by mutableStateOf(false)
        private set

    /** One-shot text for the snackbar; the screen calls [messageShown] after displaying it. */
    var message by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            if (dao.sampleCount() == 0) dao.insert(TreeMeasurement.sample(DeviceId.get(app)))
        }
    }

    fun observe(id: Long): Flow<TreeMeasurement?> = dao.observe(id)

    fun messageShown() {
        message = null
    }

    fun upload(row: TreeMeasurement) = runUpload {
        message = when (val r = uploader.upload(row)) {
            UploadOutcome.Uploaded -> str(R.string.msg_uploaded)
            UploadOutcome.NotConfigured -> str(R.string.msg_not_configured)
            is UploadOutcome.Failed -> str(R.string.msg_upload_failed, r.reason)
        }
    }

    fun uploadAll() = runUpload {
        message = when (val r = uploader.syncAll()) {
            is SyncResult.Done -> str(R.string.msg_uploaded_n, r.uploaded)
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
