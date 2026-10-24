package com.example.treamaster.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Pure helper (unit-testable): magnetic azimuth + declination -> true heading in [0, 360). */
fun trueHeadingDeg(magneticAzimuthDeg: Double, declinationDeg: Double): Double =
    ((magneticAzimuthDeg + declinationDeg) % 360.0 + 360.0) % 360.0

/**
 * Compass heading of the direction the BACK CAMERA faces, with the phone held upright (portrait).
 * Emits magnetic heading; add [declination] to get true north.
 */
class CompassSensor(context: Context) {
    private val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val isAvailable get() = rotation != null

    fun magneticHeading(): Flow<Double> = callbackFlow {
        val sensor = rotation ?: run { close(); return@callbackFlow }
        val r = FloatArray(9)
        val remapped = FloatArray(9)
        val o = FloatArray(3)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, e.values)
                // Upright phone: camera looks along the device -Z axis, so map device Z to world Y.
                SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
                SensorManager.getOrientation(remapped, o)
                trySend((Math.toDegrees(o[0].toDouble()) + 360.0) % 360.0)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sm.unregisterListener(l) }
    }

    companion object {
        /** Magnetic declination (degrees, east positive) at a place and time. */
        fun declination(latitude: Double, longitude: Double, altitudeM: Double = 0.0,
                        timeMs: Long = System.currentTimeMillis()): Double =
            GeomagneticField(latitude.toFloat(), longitude.toFloat(), altitudeM.toFloat(), timeMs)
                .declination.toDouble()
    }
}
