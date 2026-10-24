package com.example.treamaster.measure

import com.example.treamaster.geometry.Ray
import com.example.treamaster.geometry.TreeGeometry
import com.example.treamaster.geometry.Vec3
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.TrackingState

/**
 * The only class that touches ARCore. Turns a tap on the camera preview into the inputs
 * [MeasureSession] needs. Call from your GL/render thread with the latest [Frame].
 *
 * Remember `session.setDisplayGeometry(rotation, width, height)` so the projection matrix matches
 * the on-screen preview; pass the same width/height here.
 */
object ArFrameAdapter {
    private const val NEAR = 0.05f
    private const val FAR = 100f

    fun cameraPosition(frame: Frame): Vec3? {
        if (frame.camera.trackingState != TrackingState.TRACKING) return null
        val view = FloatArray(16).also { frame.camera.getViewMatrix(it, 0) }
        return TreeGeometry.cameraPosition(view)
    }

    /** 3D point on a surface under the tap (ground plane, depth point or feature point), or null. */
    fun surfacePoint(frame: Frame, xPx: Float, yPx: Float): Vec3? {
        if (frame.camera.trackingState != TrackingState.TRACKING) return null
        val hit = frame.hitTest(xPx, yPx).firstOrNull { h ->
            when (val t = h.trackable) {
                is Plane -> t.isPoseInPolygon(h.hitPose) && t.trackingState == TrackingState.TRACKING
                is DepthPoint -> true
                is Point -> true
                else -> false
            }
        } ?: return null
        val p = hit.hitPose
        return Vec3(p.tx().toDouble(), p.ty().toDouble(), p.tz().toDouble())
    }

    /** Ray through the tapped pixel (use for the tree top and the trunk edges: no surface needed). */
    fun rayThrough(frame: Frame, xPx: Float, yPx: Float, viewW: Int, viewH: Int): Ray? {
        if (frame.camera.trackingState != TrackingState.TRACKING) return null
        val view = FloatArray(16).also { frame.camera.getViewMatrix(it, 0) }
        val proj = FloatArray(16).also { frame.camera.getProjectionMatrix(it, 0, NEAR, FAR) }
        return TreeGeometry.rayFromScreen(
            xPx.toDouble(), yPx.toDouble(), viewW.toDouble(), viewH.toDouble(), view, proj,
        )
    }
}
