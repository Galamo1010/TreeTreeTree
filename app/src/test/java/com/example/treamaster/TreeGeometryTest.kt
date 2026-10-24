package com.example.treamaster

import com.example.treamaster.geometry.Ray
import com.example.treamaster.geometry.TreeGeometry
import com.example.treamaster.geometry.Vec3
import com.example.treamaster.geometry.Warning
import com.example.treamaster.location.GeoMath
import com.example.treamaster.location.LatLon
import com.example.treamaster.measure.MeasureSession
import com.example.treamaster.measure.Step
import com.example.treamaster.sensors.trueHeadingDeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

class TreeGeometryTest {

    private val camera = Vec3(0.0, 1.4, 0.0)

    private fun rayTo(from: Vec3, to: Vec3) = Ray(from, (to - from).normalized()!!)

    @Test fun centerPixelWithIdentityViewLooksDownMinusZ() {
        val view = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }
        val proj = FloatArray(16).also { it[0] = 1f; it[5] = 1f }
        val ray = TreeGeometry.rayFromScreen(540.0, 1170.0, 1080.0, 2340.0, view, proj)!!
        assertEquals(0.0, ray.direction.x, 1e-9)
        assertEquals(0.0, ray.direction.y, 1e-9)
        assertEquals(-1.0, ray.direction.z, 1e-9)
        assertEquals(0.0, ray.origin.length(), 1e-9)
    }

    @Test fun cameraPositionComesFromViewTranslation() {
        // view = translate(-2, -1.4, -3): camera sits at (2, 1.4, 3)
        val view = FloatArray(16).also {
            it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f
            it[12] = -2f; it[13] = -1.4f; it[14] = -3f
        }
        val p = TreeGeometry.cameraPosition(view)
        assertEquals(2.0, p.x, 1e-5); assertEquals(1.4, p.y, 1e-5); assertEquals(3.0, p.z, 1e-5)
    }

    @Test fun heightOfTreeStandingAboveItsBase() {
        // Base 10 m away on flat ground, true top 12 m above the ground, directly above the base.
        val base = Vec3(0.0, 0.0, -10.0)
        val top = Vec3(0.0, 12.0, -10.0)
        val r = TreeGeometry.treeHeight(camera, base, rayTo(camera, top))!!
        assertEquals(12.0, r.heightM, 1e-6)
        assertEquals(10.0, r.horizontalDistanceM, 1e-9)
        assertTrue(r.warnings.isEmpty())
    }

    @Test fun heightWarnsWhenTooClose() {
        val base = Vec3(0.0, 0.0, -3.0)
        val top = Vec3(0.0, 12.0, -3.0)
        val r = TreeGeometry.treeHeight(camera, base, rayTo(camera, top))!!
        assertTrue(Warning.TOO_CLOSE in r.warnings)
    }

    @Test fun heightRejectsStraightUpAndBelowGround() {
        val base = Vec3(0.0, 0.0, -10.0)
        assertNull(TreeGeometry.treeHeight(camera, base, Ray(camera, Vec3(0.0, 1.0, 0.0))))
        assertNull(TreeGeometry.treeHeight(camera, base, rayTo(camera, Vec3(0.0, -1.0, -10.0))))
    }

    @Test fun diameterFromTangentRays() {
        val radius = 0.25
        val axisDistance = 8.0
        val half = asin(radius / axisDistance)
        val left = Ray(camera, Vec3(-sin(half), 0.0, -cos(half)))
        val right = Ray(camera, Vec3(sin(half), 0.0, -cos(half)))
        val d = TreeGeometry.trunkDiameter(axisDistance - radius, left, right)!!
        assertEquals(0.5, d, 1e-9)
    }

    @Test fun diameterIgnoresPhoneTilt() {
        // Same silhouette but both rays pitched up 30 degrees: horizontal projection is unchanged.
        val radius = 0.25
        val half = asin(radius / 8.0)
        fun tilted(sign: Double): Ray {
            val h = Vec3(sign * sin(half), 0.0, -cos(half))
            val t = Math.toRadians(30.0)
            return Ray(camera, Vec3(h.x * cos(t), sin(t), h.z * cos(t)).normalized()!!)
        }
        // Tilting both rays by the same elevation leaves their ground-plane projection untouched.
        val d = TreeGeometry.trunkDiameter(7.75, tilted(-1.0), tilted(1.0))!!
        assertEquals(0.5, d, 1e-9)
    }

    @Test fun diameterRejectsIdenticalRays() {
        val r = Ray(camera, Vec3(0.0, 0.0, -1.0))
        assertNull(TreeGeometry.trunkDiameter(5.0, r, r))
    }

    @Test fun sessionRunsHeightThenDiameter() {
        val s = MeasureSession(measureDiameter = true)
        val base = Vec3(0.0, 0.0, -7.75)
        assertTrue(s.markBase(camera, base))
        assertTrue(s.markTop(rayTo(camera, Vec3(0.0, 12.0, -8.0))))
        assertEquals(Step.MARK_TRUNK_LEFT, s.step)
        val half = asin(0.25 / 8.0)
        assertTrue(s.markTrunkLeft(Ray(camera, Vec3(-sin(half), 0.0, -cos(half)))))
        assertTrue(s.markTrunkRight(Ray(camera, Vec3(sin(half), 0.0, -cos(half)))))
        assertEquals(Step.DONE, s.step)
        val res = s.result!!
        assertEquals(50.0, res.diameterCm!!, 1e-6)
        assertTrue(res.heightM!! in 11.0..12.5)
    }

    @Test fun sessionRejectsOutOfOrderAndTooCloseBase() {
        val s = MeasureSession()
        assertFalse(s.markTop(Ray(camera, Vec3(0.0, 1.0, -1.0))))
        assertFalse(s.markBase(camera, Vec3(0.0, 0.0, -0.1)))
    }

    @Test fun destinationNorthOneKilometre() {
        val p = GeoMath.destination(LatLon(25.0, 121.5), 0.0, 1000.0)
        assertEquals(25.0 + 1000.0 / 111_194.9, p.latitude, 1e-4)
        assertEquals(121.5, p.longitude, 1e-6)
    }

    @Test fun distanceAndBearingRoundTrip() {
        val a = LatLon(25.0, 121.5)
        val b = GeoMath.destination(a, 70.0, 250.0)
        assertEquals(250.0, GeoMath.distanceM(a, b), 0.5)
        assertEquals(70.0, GeoMath.bearingDeg(a, b), 0.1)
    }

    @Test fun trueHeadingWraps() {
        assertEquals(5.0, trueHeadingDeg(355.0, 10.0), 1e-9)
        assertEquals(355.0, trueHeadingDeg(5.0, -10.0), 1e-9)
    }
}
