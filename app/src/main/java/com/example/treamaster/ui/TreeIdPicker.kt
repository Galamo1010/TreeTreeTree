package com.example.treamaster.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.treamaster.R
import com.example.treamaster.data.Region
import com.example.treamaster.data.TreeId

/** Region dropdown (from the website) plus the slot number; together they make a tree number like "A-3". */
@Composable
fun TreeIdPicker(
    regions: List<Region>,
    regionCode: String?,
    slot: String,
    onRegionChange: (String) -> Unit,
    onSlotChange: (String) -> Unit,
    onRefreshRegions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = regions.find { it.code == regionCode }
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            RegionDropdown(regions, regionCode, onRegionChange, Modifier.weight(1.6f))
            OutlinedTextField(
                value = slot,
                onValueChange = onSlotChange,
                label = { Text(stringResource(R.string.form_slot, selected?.slotCount ?: 20)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (regions.isEmpty()) RegionsUnavailable(onRefreshRegions)
    }
}

/** Read-only dropdown of the website's regions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionDropdown(
    regions: List<Region>,
    regionCode: String?,
    onRegionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = regions.find { it.code == regionCode }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && regions.isNotEmpty() },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.let { "${it.code} · ${it.name}" } ?: regionCode.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.form_region)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            regions.forEach { r ->
                DropdownMenuItem(
                    text = { Text("${r.code} · ${r.name}") },
                    onClick = {
                        onRegionChange(r.code)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun RegionsUnavailable(onRefreshRegions: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.regions_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRefreshRegions) { Text(stringResource(R.string.regions_retry)) }
    }
}

@StringRes
fun TreeId.Problem.message(): Int = when (this) {
    TreeId.Problem.NO_REGION -> R.string.err_no_region
    TreeId.Problem.BAD_SLOT -> R.string.err_bad_slot
    TreeId.Problem.SLOT_OUT_OF_RANGE -> R.string.err_slot_range
}
