package com.example.treamaster

import com.example.treamaster.data.TreeId
import com.example.treamaster.data.TreeMeasurement
import com.example.treamaster.export.TreeCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTest {
    @Test fun treeIdFormat() {
        listOf("A-1", "AB12-34", "ABCDEF-100").forEach { assertTrue(it, TreeId.isValid(it)) }
        listOf("a-1", "A-0", "A-", "-1", "ABCDEFG-1", "A_1", "A-01", "").forEach { assertFalse(it, TreeId.isValid(it)) }
        assertEquals("A-1", TreeId.normalize("  a-1 "))
    }

    @Test fun csvHasHeaderEmptyCellsAndEscaping() {
        val m = TreeMeasurement(
            uuid = "u1", treeId = "A-1", species = null, heightM = 12.5, diameterCm = null,
            distanceToTreeM = 8.0, latitude = null, longitude = null, altitudeM = null,
            gpsAccuracyM = null, bearingDeg = null, note = "有,逗號 \"引號\"", deviceId = "d1",
            createdAtMs = 0,
        )
        val lines = TreeCsv.build(listOf(m)).split("\r\n")
        assertTrue(lines[0].startsWith("id,tree_id,species,height_m"))
        assertEquals("u1,A-1,,12.5,,8.0,,,,,,\"有,逗號 \"\"引號\"\"\",d1,1970-01-01T00:00:00Z", lines[1])
    }
}
