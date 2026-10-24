package com.example.treamaster.export

import com.example.treamaster.data.TreeMeasurement
import java.time.Instant

/** CSV with the same column names as the cloud table `tree_measurements`. */
object TreeCsv {
    private val header = listOf(
        "id", "tree_id", "species", "height_m", "diameter_cm", "distance_to_tree_m",
        "latitude", "longitude", "altitude_m", "gps_accuracy_m", "bearing_deg",
        "note", "device_id", "created_at",
    )

    fun build(rows: List<TreeMeasurement>): String = buildString {
        append(header.joinToString(",")).append("\r\n")
        for (m in rows) {
            val cells = listOf(
                m.uuid, m.treeId, m.species, m.heightM, m.diameterCm, m.distanceToTreeM,
                m.latitude, m.longitude, m.altitudeM, m.gpsAccuracyM, m.bearingDeg,
                m.note, m.deviceId, Instant.ofEpochMilli(m.createdAtMs),
            )
            append(cells.joinToString(",") { escape(it?.toString().orEmpty()) }).append("\r\n")
        }
    }

    private fun escape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
