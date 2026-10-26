package com.example.treamaster

import com.example.treamaster.data.Region
import com.example.treamaster.data.TreeId
import com.example.treamaster.data.TreeMeasurement
import com.example.treamaster.sync.SupabaseUploader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class UploadMappingTest {
    private val regionA = Region("A", "校門口", slotCount = 20)

    @Test fun buildsTreeIdFromRegionAndSlot() {
        assertEquals(TreeId.Built.Ok("A-3"), TreeId.build(regionA, " 3 "))
        assertEquals(TreeId.Built.Blank, TreeId.build(regionA, ""))
        assertEquals(TreeId.Built.Blank, TreeId.build(null, " "))
        assertEquals(TreeId.Built.Invalid(TreeId.Problem.NO_REGION), TreeId.build(null, "3"))
        listOf("0", "-1", "1.5", "x").forEach {
            assertEquals(it, TreeId.Built.Invalid(TreeId.Problem.BAD_SLOT), TreeId.build(regionA, it))
        }
        assertEquals(TreeId.Built.Invalid(TreeId.Problem.SLOT_OUT_OF_RANGE), TreeId.build(regionA, "21"))
    }

    @Test fun splitsTreeId() {
        assertEquals("AB1" to "12", TreeId.split("AB1-12"))
        assertNull(TreeId.split(null))
        assertNull(TreeId.split("bad"))
    }

    @Test fun treeValueUsesWebsiteUnitsAndLocalDate() {
        val m = measurement(heightM = 1.984, diameterCm = 4.85)
        // 2026-10-25T17:00Z is already 10-26 in Taiwan.
        val json = SupabaseUploader.toTreeValue(m, ZoneId.of("Asia/Taipei"))
        assertEquals("A-3", json.getString("tree_id"))
        assertEquals(198.4, json.getDouble("height"), 1e-9)
        assertEquals(4.85, json.getDouble("diameter"), 1e-9)
        assertEquals("樟樹", json.getString("species"))
        assertEquals("2026-10-26", json.getString("date"))
    }

    @Test fun missingValuesAreLeftOutSoTheWebsiteKeepsThem() {
        val json = SupabaseUploader.toTreeValue(measurement(heightM = 2.0, diameterCm = null, species = null), ZoneId.of("UTC"))
        assertFalse(json.has("diameter"))
        assertFalse(json.has("species"))
    }

    @Test fun sampleSetFillsOneRegionInOrder() {
        val samples = TreeMeasurement.samples("d1", nowMs = 1_000)
        assertEquals(TreeMeasurement.SAMPLE_COUNT, samples.size)
        assertEquals((1_000L until 1_020L).toList(), samples.map { it.createdAtMs })
        samples.forEach {
            assertTrue(it.isSample && it.treeId == null)
            assertTrue(it.heightM!! in 1.5..3.5 && it.diameterCm!! in 3.0..7.0)
        }
    }

    private fun measurement(heightM: Double?, diameterCm: Double?, species: String? = "樟樹") = TreeMeasurement(
        treeId = "A-3", species = species, heightM = heightM, diameterCm = diameterCm,
        distanceToTreeM = 5.0, latitude = null, longitude = null, altitudeM = null,
        gpsAccuracyM = null, bearingDeg = null, createdAtMs = 1_792_947_600_000,
    )
}
