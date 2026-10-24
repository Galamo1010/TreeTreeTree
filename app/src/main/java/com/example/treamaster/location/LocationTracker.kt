package com.example.treamaster.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** One GPS reading. Altitude is metres above the WGS84 ellipsoid (null if the device did not report it). */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val accuracyM: Float,
    val timeMs: Long,
) {
    val grade: AccuracyGrade get() = AccuracyGrade.of(accuracyM)
}

enum class AccuracyGrade {
    GOOD, FAIR, POOR;

    companion object {
        const val GOOD_MAX_M = 15f
        const val FAIR_MAX_M = 50f

        fun of(accuracyM: Float) = when {
            accuracyM <= GOOD_MAX_M -> GOOD
            accuracyM <= FAIR_MAX_M -> FAIR
            else -> POOR
        }
    }
}

/**
 * Streams high-accuracy location fixes using Google's Fused Location Provider.
 * Collect [updates] while the measure screen is visible; cancelling the collector stops GPS.
 */
class LocationTracker(context: Context) {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // guarded by hasPermission()
    fun updates(intervalMs: Long = 2_000L): Flow<LocationFix> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val l = result.lastLocation ?: return
                trySend(
                    LocationFix(
                        latitude = l.latitude,
                        longitude = l.longitude,
                        altitudeM = if (l.hasAltitude()) l.altitude else null,
                        accuracyM = if (l.hasAccuracy()) l.accuracy else Float.MAX_VALUE,
                        timeMs = l.time,
                    )
                )
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
