package com.example.treamaster.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.treamaster.R

@Composable
fun HomeScreen(onUpload: () -> Unit, onMeasure: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Button(onClick = onMeasure, modifier = Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp)) {
                Text(stringResource(R.string.home_measure), style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = onUpload, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.home_upload), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
