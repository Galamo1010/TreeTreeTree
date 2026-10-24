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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.treamaster.R
import com.example.treamaster.data.TreeMeasurement
import com.example.treamaster.export.CsvShare
import com.example.treamaster.ui.formatTime
import com.example.treamaster.ui.summary
import com.example.treamaster.ui.title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadListScreen(vm: UploadViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val rows by vm.rows.collectAsState()
    val context = LocalContext.current
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
                actions = {
                    TextButton(onClick = { CsvShare.share(context, rows) }, enabled = rows.isNotEmpty()) {
                        Text(stringResource(R.string.upload_export_csv))
                    }
                    TextButton(onClick = vm::uploadAll, enabled = !vm.uploading && rows.any { it.syncedAtMs == null }) {
                        Text(stringResource(R.string.upload_all))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (vm.uploading) LinearProgressIndicator(Modifier.align(Alignment.TopCenter))
            if (rows.isEmpty()) {
                Text(stringResource(R.string.upload_empty), Modifier.align(Alignment.Center))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { it.id }) { row ->
                        UploadRow(row, onClick = { onOpen(row.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadRow(row: TreeMeasurement, onClick: () -> Unit) {
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

/** Shows [UploadViewModel.message] once, shared by the list and detail screens. */
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
