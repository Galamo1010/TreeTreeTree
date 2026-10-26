package com.example.treamaster.ui.measure

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.treamaster.R
import com.example.treamaster.geometry.Warning
import com.example.treamaster.location.AccuracyGrade
import com.example.treamaster.measure.Step
import com.example.treamaster.ui.ar.ArCameraView
import com.example.treamaster.ui.TreeIdPicker
import com.example.treamaster.ui.ar.ArController
import com.example.treamaster.ui.summary
import com.example.treamaster.ui.title

private val Scrim = Color(0x99000000)

/**
 * Camera preview with a crosshair. The user aims and presses the step button:
 * base -> top -> trunk left -> trunk right, then fills in the form and saves to measure the next tree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureScreen(
    locationGranted: Boolean,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    vm: MeasureViewModel = viewModel(),
) {
    val ar = remember { ArController() }
    var tracking by remember { mutableStateOf(false) }
    var showSession by remember { mutableStateOf(false) }
    val sessionRows by vm.sessionRows.collectAsState()
    val form = vm.form

    BackHandler(onBack = onBack)
    LaunchedEffect(locationGranted) { if (locationGranted) vm.trackLocation() }
    LaunchedEffect(Unit) { vm.trackCompass() }

    Box(Modifier.fillMaxSize()) {
        ArCameraView(controller = ar, onTrackingChanged = { tracking = it })
        if (form == null) Crosshair()

        Column(Modifier.fillMaxWidth().background(Scrim).statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
                }
                Text(stringResource(R.string.measure_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showSession = true }) {
                    Text(stringResource(R.string.measure_session_button, sessionRows.size), color = Color.White)
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(if (tracking) R.string.tracking_ok else R.string.tracking_searching),
                    color = Color.White, style = MaterialTheme.typography.bodySmall,
                )
                if (locationGranted) GpsStatus(vm)
            }
        }

        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (form == null) StepPanel(vm, ar, tracking) else ResultPanel(vm, form)
        }
    }

    if (showSession) {
        ModalBottomSheet(onDismissRequest = { showSession = false }) {
            Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
                Text(stringResource(R.string.session_title), style = MaterialTheme.typography.titleLarge)
                if (sessionRows.isEmpty()) {
                    Text(stringResource(R.string.session_empty), modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(sessionRows, key = { it.id }) { row ->
                            ListItem(headlineContent = { Text(row.title()) }, supportingContent = { Text(row.summary()) })
                            HorizontalDivider()
                        }
                    }
                }
                Text(
                    stringResource(R.string.session_finish_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = {
                        showSession = false
                        vm.finishSession(onFinished)
                    },
                    enabled = sessionRows.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                ) { Text(stringResource(R.string.session_finish)) }
            }
        }
    }
}

@Composable
private fun GpsStatus(vm: MeasureViewModel) {
    val fix = vm.fix
    val text = when {
        fix == null -> stringResource(R.string.gps_none)
        fix.grade == AccuracyGrade.POOR -> stringResource(R.string.gps_poor)
        else -> stringResource(R.string.gps_status, fix.accuracyM)
    }
    Text(text, color = Color.White, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Crosshair() {
    Canvas(Modifier.fillMaxSize()) {
        val c = center
        val arm = 18.dp.toPx()
        val gap = 4.dp.toPx()
        val stroke = 2.dp.toPx()
        val color = Color.White
        drawLine(color, Offset(c.x - arm, c.y), Offset(c.x - gap, c.y), stroke)
        drawLine(color, Offset(c.x + gap, c.y), Offset(c.x + arm, c.y), stroke)
        drawLine(color, Offset(c.x, c.y - arm), Offset(c.x, c.y - gap), stroke)
        drawLine(color, Offset(c.x, c.y + gap), Offset(c.x, c.y + arm), stroke)
    }
}

@Composable
private fun StepPanel(vm: MeasureViewModel, ar: ArController, tracking: Boolean) {
    val (instruction, action) = when (vm.step) {
        Step.MARK_BASE -> R.string.step_base to R.string.action_base
        Step.MARK_TOP -> R.string.step_top to R.string.action_top
        Step.MARK_TRUNK_LEFT -> R.string.step_left to R.string.action_left
        Step.MARK_TRUNK_RIGHT, Step.DONE -> R.string.step_right to R.string.action_right
    }
    Column(
        Modifier.fillMaxWidth().background(Scrim).navigationBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(instruction), color = Color.White, style = MaterialTheme.typography.titleMedium)
        vm.hint?.let {
            Text(stringResource(it), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.padding(top = 4.dp))
        }
        Button(
            onClick = { vm.markAtCrosshair(ar) },
            enabled = tracking && !vm.busy,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text(stringResource(action)) }
        if (vm.step != Step.MARK_BASE) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::redo, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_redo), color = Color.White)
                }
                if (vm.step == Step.MARK_TRUNK_LEFT || vm.step == Step.MARK_TRUNK_RIGHT) {
                    OutlinedButton(onClick = vm::skipDiameter, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_skip_diameter), color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultPanel(vm: MeasureViewModel, form: ResultForm) {
    Surface(Modifier.fillMaxWidth().imePadding(), tonalElevation = 3.dp) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.form_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.form_distance, form.distanceM), style = MaterialTheme.typography.bodySmall)
            if (Warning.TOO_CLOSE in form.warnings) {
                Text(stringResource(R.string.form_warn_too_close), color = MaterialTheme.colorScheme.error)
            }
            if (Warning.TOP_NOT_ABOVE_CAMERA_LEVEL_SUSPECT in form.warnings) {
                Text(stringResource(R.string.form_warn_top), color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.height,
                    onValueChange = { v -> vm.updateForm { it.copy(height = v) } },
                    label = { Text(stringResource(R.string.form_height)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.diameter,
                    onValueChange = { v -> vm.updateForm { it.copy(diameter = v) } },
                    label = { Text(stringResource(R.string.form_diameter)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            val regions by vm.regions.collectAsState()
            Text(stringResource(R.string.form_tree_id), style = MaterialTheme.typography.labelMedium)
            TreeIdPicker(
                regions = regions,
                regionCode = form.regionCode,
                slot = form.slot,
                onRegionChange = { code -> vm.updateForm { it.copy(regionCode = code) } },
                onSlotChange = { v -> vm.updateForm { it.copy(slot = v) } },
                onRefreshRegions = vm::refreshRegions,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.species,
                onValueChange = { v -> vm.updateForm { it.copy(species = v) } },
                label = { Text(stringResource(R.string.form_species)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.note,
                onValueChange = { v -> vm.updateForm { it.copy(note = v) } },
                label = { Text(stringResource(R.string.form_note)) },
                modifier = Modifier.fillMaxWidth(),
            )
            form.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::redo, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_redo))
                }
                Button(onClick = vm::saveAndNext, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.form_save_next))
                }
            }
        }
    }
}
