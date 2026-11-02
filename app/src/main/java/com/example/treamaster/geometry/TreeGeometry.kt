package com.example.treamaster.geometry

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

enum class Warning {
    /** Camera closer than half the tree height: the top ray is steep and error grows. */
    TOO_CLOSE,

    /** Top ray points almost straight ahead (below ~5 deg), probably the base was marked twice. */
    TOP_NOT_ABOVE_CAMERA_LEVEL_SUSPECT,
}

data class HeightResult(
    val heightM: Double,
    val horizontalDistanceM: Double,
    val warnings: Set<Warning>,
)

object TreeGeometry {

    /**
     * Turn a screen pixel into a world-space ray.
     *
     * [view] and [projection] are the column-major 4x4 matrices ARCore gives you
     * (`camera.getViewMatrix`, `camera.getProjectionMatrix`). Pixel (0,0) is top-left.
     * Assumes a perspective projection with optional principal-point offset (P[8], P[9]).
     */
    fun rayFromScreen(
        px: Double, py: Double,
        viewWidth: Double, viewHeight: Double,
        view: FloatArray, projection: FloatArray,
    ): Ray? {
        if (viewWidth <= 0 || viewHeight <= 0 || view.size < 16 || projection.size < 16) return null
        val p0 = projection[0].toDouble()
        val p5 = projection[5].toDouble()
        if (p0 == 0.0 || p5 == 0.0) return null

        val ndcX = 2.0 * px / viewWidth - 1.0
        val ndcY = 1.0 - 2.0 * py / viewHeight

        // Point on the z = -1 plane of view space that projects to (ndcX, ndcY).
        val vx = (ndcX + projection[8]) / p0
        val vy = (ndcY + projection[9]) / p5
        val dirView = Vec3(vx, vy, -1.0).normalized() ?: return null

        // View matrix = [R | t]. World = R^T * view, camera position = -R^T * t.
        fun v(i: Int) = view[i].toDouble()
        val dir = Vec3(
            v(0) * dirView.x + v(1) * dirView.y + v(2) * dirView.z,
            v(4) * dirView.x + v(5) * dirView.y + v(6) * dirView.z,
            v(8) * dirView.x + v(9) * dirView.y + v(10) * dirView.z,
        ).normalized() ?: return null
        val tx = v(12); val ty = v(13); val tz = v(14)
        val origin = Vec3(
            -(v(0) * tx + v(1) * ty + v(2) * tz),
            -(v(4) * tx + v(5) * ty + v(6) * tz),
            -(v(8) * tx + v(9) * ty + v(10) * tz),
        )
        return Ray(origin, dir)
    }

    /** Camera position extracted from a column-major view matrix. */
    fun cameraPosition(view: FloatArray): Vec3 =
        rayFromScreen(0.5, 0.5, 1.0, 1.0, view, IDENTITY_PROJECTION)!!.origin

    private val IDENTITY_PROJECTION = FloatArray(16).also { it[0] = 1f; it[5] = 1f }

    /**
     * Tree height from the marked base point and a ray through the tree top.
     *
     * The base is a real 3D point (hit-test on the ground). The top has no surface to hit, so only its
     * ray is used: we extend it until its horizontal reach equals the horizontal distance to the base.
     * Distance and camera height are taken from the ray's own origin, so the user may step back
     * after marking the base to see the top.
     *
     * height = (cameraY + D * tan(elevation of top ray)) - baseY
     */
    fun treeHeight(base: Vec3, topRay: Ray): HeightResult? {
        val camera = topRay.origin
        val d = hypot(base.x - camera.x, base.z - camera.z)
        if (d < MIN_DISTANCE_M) return null
        val horiz = topRay.direction.horizontalLength()
        if (horiz < 1e-6) return null // looking straight up/down
        val topY = camera.y + topRay.direction.y / horiz * d
        val height = topY - base.y
        if (height <= 0) return null

        val warnings = mutableSetOf<Warning>()
        if (d < height / 2.0) warnings += Warning.TOO_CLOSE
        val elevationDeg = Math.toDegrees(atan2(topRay.direction.y, horiz))
        if (elevationDeg < 5.0 && topY - camera.y < height * 0.1) {
            warnings += Warning.TOP_NOT_ABOVE_CAMERA_LEVEL_SUSPECT
        }
        return HeightResult(height, d, warnings)
    }

    /**
     * Trunk diameter by tangent projection.
     *
     * Seen from distance c to the trunk's axis, a silhouette edge makes an angle a with the axis
     * direction where sin(a) = r / c. If d is the distance to the near surface, c = d + r, so
     * r = d * s / (1 - s), s = sin(a). Each edge is solved on its own against the direction from its
     * ray's origin to [base] (the marked trunk foot, taken as the axis direction and as the near
     * surface), and the two half-widths are added. So the edges may be marked from different spots,
     * and a base marked a little off-centre mostly cancels out. Angles are measured in the ground
     * plane, so the tilt of the phone does not matter. Left and right may be given in either order.
     *
     * @return diameter in metres, or null if the geometry is degenerate.
     */
    fun trunkDiameter(base: Vec3, leftEdge: Ray, rightEdge: Ray): Double? {
        val a = edgeSide(base, leftEdge) ?: return null
        val b = edgeSide(base, rightEdge) ?: return null
        // The edge with the larger signed angle bounds one side of the axis, the other edge the other.
        val (hi, lo) = if (a.angle >= b.angle) a to b else b to a
        if (hi.angle - lo.angle <= 1e-4) return null
        val r1 = halfWidth(hi.distance, hi.angle) ?: return null
        val r2 = halfWidth(lo.distance, -lo.angle) ?: return null
        return (r1 + r2).takeIf { it > 0 }
    }

    private class EdgeSide(val distance: Double, val angle: Double)

    /** Horizontal distance origin -> base and the signed ground-plane angle from that direction to the ray. */
    private fun edgeSide(base: Vec3, edge: Ray): EdgeSide? {
        val d = horizontalDistance(edge.origin, base)
        if (d < MIN_DISTANCE_M) return null
        val axis = Vec3(base.x - edge.origin.x, 0.0, base.z - edge.origin.z).normalized() ?: return null
        val e = Vec3(edge.direction.x, 0.0, edge.direction.z).normalized() ?: return null
        val angle = atan2(axis.x * e.z - axis.z * e.x, axis.dot(e))
        return EdgeSide(d, angle)
    }

    private fun halfWidth(nearSurfaceDistance: Double, angle: Double): Double? {
        val s = sin(angle)
        if (s >= 0.999) return null
        return nearSurfaceDistance * s / (1.0 - s)
    }

    /**
     * Point on [ray] whose horizontal distance from the ray's origin equals that of [target]. This is
     * where the tree top / trunk edge is assumed to be, so it is where to draw its marker.
     */
    fun pointAtHorizontalDistance(ray: Ray, target: Vec3): Vec3? {
        val horiz = ray.direction.horizontalLength()
        if (horiz < 1e-6) return null
        return ray.origin + ray.direction * (horizontalDistance(ray.origin, target) / horiz)
    }

    /** Horizontal distance camera -> point. */
    fun horizontalDistance(a: Vec3, b: Vec3) = hypot(a.x - b.x, a.z - b.z)

    /** Angle of elevation in degrees of a ray above the horizon (negative = below). */
    fun elevationDeg(ray: Ray): Double =
        Math.toDegrees(atan2(ray.direction.y, ray.direction.horizontalLength()))

    /** Pure trigonometric height when you already know the distance and two elevation angles. */
    fun heightFromAngles(distanceM: Double, topElevationDeg: Double, baseElevationDeg: Double): Double =
        distanceM * (tan(Math.toRadians(topElevationDeg)) - tan(Math.toRadians(baseElevationDeg)))

    /** Height above the marked base at which girth is conventionally taken (breast height). */
    const val BREAST_HEIGHT_M = 1.3

    private const val MIN_DISTANCE_M = 0.3
}
