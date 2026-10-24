package com.example.treamaster.measure

import com.example.treamaster.geometry.Ray
import com.example.treamaster.geometry.TreeGeometry
import com.example.treamaster.geometry.Vec3
import com.example.treamaster.geometry.Warning

enum class Step { MARK_BASE, MARK_TOP, MARK_TRUNK_LEFT, MARK_TRUNK_RIGHT, DONE }

data class MeasureResult(
    val heightM: Double?,
    val diameterCm: Double?,
    val distanceToTreeM: Double,
    val warnings: Set<Warning>,
)

/**
 * Pure state machine for one tree measurement. No Android or ARCore types, so a ViewModel can own it
 * and JVM unit tests can drive it.
 *
 * Flow: base -> top -> (optional) trunk left edge -> trunk right edge -> DONE.
 * For diameter, aim the left/right edges at breast height ([TreeGeometry.BREAST_HEIGHT_M] above the base).
 */
class MeasureSession(private val measureDiameter: Boolean = true) {
    var step: Step = Step.MARK_BASE
        private set

    private var camera: Vec3? = null
    private var base: Vec3? = null
    private var top: Ray? = null
    private var left: Ray? = null
    private var right: Ray? = null
    private var computed: MeasureResult? = null

    val result: MeasureResult? get() = computed

    /** @param camera camera position at the moment of marking, @param basePoint ground hit at the trunk foot. */
    fun markBase(camera: Vec3, basePoint: Vec3): Boolean {
        if (step != Step.MARK_BASE) return false
        if (TreeGeometry.horizontalDistance(camera, basePoint) < 0.3) return false
        this.camera = camera
        this.base = basePoint
        step = Step.MARK_TOP
        return true
    }

    fun markTop(ray: Ray): Boolean {
        if (step != Step.MARK_TOP) return false
        val h = TreeGeometry.treeHeight(camera!!, base!!, ray) ?: return false
        top = ray
        step = if (measureDiameter) Step.MARK_TRUNK_LEFT else Step.DONE
        if (step == Step.DONE) finish()
        return h.heightM > 0
    }

    fun markTrunkLeft(ray: Ray): Boolean {
        if (step != Step.MARK_TRUNK_LEFT) return false
        left = ray
        step = Step.MARK_TRUNK_RIGHT
        return true
    }

    fun markTrunkRight(ray: Ray): Boolean {
        if (step != Step.MARK_TRUNK_RIGHT) return false
        val d = TreeGeometry.horizontalDistance(camera!!, base!!)
        TreeGeometry.trunkDiameter(d, left!!, ray) ?: return false
        right = ray
        step = Step.DONE
        finish()
        return true
    }

    /** Skip the diameter and finish with the height only. */
    fun skipDiameter() {
        if (step == Step.MARK_TRUNK_LEFT || step == Step.MARK_TRUNK_RIGHT) {
            step = Step.DONE
            finish()
        }
    }

    /** Re-do the whole measurement. */
    fun reset() {
        step = Step.MARK_BASE
        camera = null; base = null; top = null; left = null; right = null; computed = null
    }

    private fun finish() {
        val cam = camera!!
        val b = base!!
        val h = top?.let { TreeGeometry.treeHeight(cam, b, it) }
        val d = TreeGeometry.horizontalDistance(cam, b)
        val dia = if (left != null && right != null) TreeGeometry.trunkDiameter(d, left!!, right!!) else null
        computed = MeasureResult(
            heightM = h?.heightM,
            diameterCm = dia?.times(100.0),
            distanceToTreeM = d,
            warnings = h?.warnings ?: emptySet(),
        )
    }
}
