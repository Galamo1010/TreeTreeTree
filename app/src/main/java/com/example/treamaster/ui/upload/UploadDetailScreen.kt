package com.example.treamaster.ui.upload

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.treamaster.R
import com.example.treamaster.data.TreeMeasurement
import com.example.treamaster.ui.formatOrDash
import com.example.treamaster.ui.formatTime
import com.example.treamaster.ui.title
import java.util.Locale

/** One record: its values plus 上傳 / 刪除. The sample row cannot be deleted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadDetailScreen(vm: UploadViewModel, id: Long, onBack: () -> Unit) {
    val row by remember(id) { vm.observe(id) }.collectAsState(initial = null)
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)
    MessageSnackbar(vm, snackbar)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(row?.title() ?: stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val m = row ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Fields(m)

            m.syncedAtMs?.let {
                Text(stringResource(R.string.detail_synced_at, formatTime(it)), color = MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = !m.isSample,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.detail_delete)) }
                Button(
                    onClick = { vm.upload(m) },
                    enabled = m.syncedAtMs == null && !vm.uploading,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(if (m.syncedAtMs == null) R.string.detail_upload else R.string.status_synced)) }
            }
            if (m.isSample) {
                Text(stringResource(R.string.detail_sample_no_delete), style = MaterialTheme.typography.bodySmall)
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.delete_confirm_title)) },
                text = {
                    Text(
                        stringResource(
                            if (m.syncedAtMs == null) R.string.delete_confirm_body_unsynced else R.string.delete_confirm_body,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        vm.delete(m, onDone = onBack)
                    }) { Text(stringResource(R.string.detail_delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
    }
}

@Composable
private fun Fields(m: TreeMeasurement) {
    val dash = stringResource(R.string.value_missing)
    Field(R.string.field_created_at, formatTime(m.createdAtMs))
    Field(R.string.field_tree_id, m.treeId ?: dash)
    Field(R.string.field_species, m.species ?: dash)
    Field(R.string.field_height, formatOrDash(m.heightM, 2) + " m")
    Field(R.string.field_diameter, formatOrDash(m.diameterCm, 1) + " cm")
    Field(R.string.field_distance, formatOrDash(m.distanceToTreeM, 1) + " m")
    Field(
        R.string.field_location,
        if (m.latitude != null && m.longitude != null) {
            String.format(Locale.US, "%.6f, %.6f", m.latitude, m.longitude)
        } else dash,
    )
    Field(R.string.field_gps_accuracy, m.gpsAccuracyM?.let { String.format(Locale.US, "±%.0f m", it) } ?: dash)
    Field(R.string.field_note, m.note.ifEmpty { dash })
}

@Composable
private fun Field(label: Int, value: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}
