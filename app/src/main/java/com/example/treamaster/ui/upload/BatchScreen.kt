package com.example.treamaster.ui.upload

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.treamaster.R
import com.example.treamaster.data.TreeId
import com.example.treamaster.data.TreeMeasurement
import com.example.treamaster.export.CsvShare
import com.example.treamaster.ui.RegionDropdown
import com.example.treamaster.ui.RegionsUnavailable
import com.example.treamaster.ui.formatTime
import com.example.treamaster.ui.summary
import com.example.treamaster.ui.title

/** One scan: its trees, plus upload / export / delete for the whole scan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchScreen(vm: UploadViewModel, batchId: String, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val rows by remember(batchId) { vm.observeBatch(batchId) }.collectAsState(initial = emptyList())
    val batch = vm.batches.collectAsState().value.find { it.batchId == batchId }
    val isSample = batchId == TreeMeasurement.SAMPLE_BATCH
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)
    MessageSnackbar(vm, snackbar)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(batch?.title() ?: stringResource(R.string.upload_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { CsvShare.share(context, rows) }, enabled = rows.isNotEmpty()) {
                        Text(stringResource(R.string.upload_export_csv))
                    }
                    TextButton(
                        onClick = { vm.uploadBatch(batchId) },
                        enabled = !vm.uploading && rows.any { it.syncedAtMs == null || (it.isSample && it.treeId != null) },
                    ) { Text(stringResource(R.string.upload_all)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (vm.uploading) LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth())
            LazyColumn(Modifier.fillMaxSize()) {
                if (isSample) item(key = "samples") { SampleHeader(vm, rows) }
                items(rows, key = { it.id }) { row ->
                    TreeRow(row, onClick = { onOpen(row.id) })
                    HorizontalDivider()
                }
                if (!isSample && rows.isNotEmpty()) {
                    item(key = "delete") {
                        TextButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        ) { Text(stringResource(R.string.batch_delete), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val unsynced = rows.count { it.syncedAtMs == null }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.batch_delete_confirm_title, rows.size)) },
            text = {
                Text(
                    if (unsynced > 0) stringResource(R.string.batch_delete_confirm_unsynced, unsynced)
                    else stringResource(R.string.delete_confirm_body),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteBatch(batchId, onDone = onBack)
                }) { Text(stringResource(R.string.detail_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun TreeRow(row: TreeMeasurement, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(row.title()) },
        supportingContent = { Text(row.summary() + "\n" + formatTime(row.createdAtMs)) },
        trailingContent = {
            val synced = row.syncedAtMs != null
            Text(
                stringResource(if (synced) R.string.status_synced else R.string.status_pending),
                color = if (synced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        },
    )
}

/** Debug-only test scan: pick one region and all test trees get its slots 1-20. */
@Composable
private fun SampleHeader(vm: UploadViewModel, samples: List<TreeMeasurement>) {
    var choosing by remember { mutableStateOf(false) }
    val assigned = TreeId.split(samples.firstNotNullOfOrNull { it.treeId })?.first
    ListItem(
        headlineContent = { Text(stringResource(R.string.samples_title, samples.size)) },
        supportingContent = {
            Text(
                if (assigned != null) stringResource(R.string.samples_assigned, assigned)
                else stringResource(R.string.samples_unassigned),
            )
        },
        trailingContent = {
            TextButton(onClick = { choosing = true }) { Text(stringResource(R.string.samples_choose_region)) }
        },
    )
    HorizontalDivider()

    if (choosing) {
        val regions by vm.regions.collectAsState()
        var code by remember { mutableStateOf(assigned) }
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.samples_choose_region)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RegionDropdown(regions, code, onRegionChange = { code = it }, Modifier.fillMaxWidth())
                    if (regions.isEmpty()) RegionsUnavailable(vm::refreshRegions)
                    Text(stringResource(R.string.samples_overwrite_warning), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        code?.let(vm::assignSamplesToRegion)
                        choosing = false
                    },
                    enabled = code != null,
                ) { Text(stringResource(R.string.samples_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
