package com.example.treamaster.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.treamaster.data.TreeMeasurement
import java.io.File

object CsvShare {
    /** Writes the CSV to the cache dir and opens the system share sheet. */
    fun share(context: Context, rows: List<TreeMeasurement>) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "trees_${System.currentTimeMillis()}.csv")
        // BOM so Excel opens the UTF-8 (Chinese notes) correctly.
        file.writeText("﻿" + TreeCsv.build(rows), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    }
}
