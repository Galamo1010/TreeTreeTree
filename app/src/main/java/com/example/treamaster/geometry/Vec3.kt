package com.example.treamaster.geometry

import kotlin.math.hypot
import kotlin.math.sqrt

/** World-space vector. Convention (same as ARCore): +Y is up, ground is the XZ plane. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = Vec3(x * k, y * k, z * k)

    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun length() = sqrt(dot(this))

    /** Length of the projection onto the ground plane. */
    fun horizontalLength() = hypot(x, z)

    fun normalized(): Vec3? {
        val l = length()
        return if (l < 1e-9) null else Vec3(x / l, y / l, z / l)
    }
}

/** A half-line from the camera through a screen pixel. [direction] is unit length. */
data class Ray(val origin: Vec3, val direction: Vec3)
