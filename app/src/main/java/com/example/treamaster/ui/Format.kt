package com.example.treamaster.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.treamaster.R
import com.example.treamaster.data.TreeMeasurement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Number with fixed decimals, or "—" when missing. */
@Composable
fun formatOrDash(v: Double?, decimals: Int): String =
    v?.let { String.format(Locale.US, "%.${decimals}f", it) } ?: stringResource(R.string.value_missing)

fun formatTime(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

/** List title: the sample label (with its number once given), the seedling number, or "未編號". */
@Composable
fun TreeMeasurement.title(): String = when {
    isSample -> stringResource(R.string.sample_label) + (treeId?.let { " $it" } ?: "")
    treeId != null -> treeId
    else -> stringResource(R.string.no_tree_id)
}

@Composable
fun TreeMeasurement.summary(): String =
    stringResource(R.string.row_summary, formatOrDash(heightM, 2), formatOrDash(diameterCm, 1)) +
        (species?.let { " · $it" } ?: "")
