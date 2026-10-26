package com.example.treamaster.ui.upload

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.treamaster.R
import com.example.treamaster.data.Batch
import com.example.treamaster.data.TreeMeasurement
import com.example.treamaster.ui.formatTime

/** Upload screen: one entry per scan (a finished measuring run); opening it shows its trees. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadListScreen(vm: UploadViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val batches by vm.batches.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    BackHandler(onBack = onBack)
    MessageSnackbar(vm, snackbar)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upload_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (batches.isEmpty()) {
                Text(stringResource(R.string.upload_empty), Modifier.align(Alignment.Center))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(batches, key = { it.batchId }) { batch ->
                        BatchRow(batch, onClick = { onOpen(batch.batchId) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchRow(batch: Batch, onClick: () -> Unit) {
    val done = batch.syncedCount == batch.treeCount
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(batch.title()) },
        supportingContent = { Text(stringResource(R.string.batch_summary, batch.treeCount, batch.syncedCount)) },
        trailingContent = {
            Text(
                stringResource(if (done) R.string.status_synced else R.string.status_pending),
                color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        },
    )
}

/** "測試資料", "先前的資料", or when the scan started. */
@Composable
internal fun Batch.title(): String = when (batchId) {
    TreeMeasurement.SAMPLE_BATCH -> stringResource(R.string.batch_sample_title)
    TreeMeasurement.LEGACY_BATCH -> stringResource(R.string.batch_legacy_title)
    else -> stringResource(R.string.batch_title, formatTime(startedAtMs))
}

/** Shows [UploadViewModel.message] once, shared by the upload screens. */
@Composable
internal fun MessageSnackbar(vm: UploadViewModel, host: SnackbarHostState) {
    val message = vm.message
    LaunchedEffect(message) {
        if (message != null) {
            vm.messageShown()
            host.showSnackbar(message)
        }
    }
}
