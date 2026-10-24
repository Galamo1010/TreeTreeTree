package com.example.treamaster.ui.measure

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.treamaster.R
import com.example.treamaster.data.DeviceId
import com.example.treamaster.data.TreeDatabase
import com.example.treamaster.data.TreeId
import com.example.treamaster.data.TreeMeasurement
import com.example.treamaster.geometry.Warning
import com.example.treamaster.location.GeoMath
import com.example.treamaster.location.LatLon
import com.example.treamaster.location.LocationFix
import com.example.treamaster.location.LocationTracker
import com.example.treamaster.measure.ArFrameAdapter
import com.example.treamaster.measure.MeasureSession
import com.example.treamaster.measure.Step
import com.example.treamaster.sensors.CompassSensor
import com.example.treamaster.sensors.trueHeadingDeg
import com.example.treamaster.ui.ar.ArController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** Editable result shown after the last marking step. Text fields hold what the user typed. */
data class ResultForm(
    val height: String,
    val diameter: String,
    val treeId: String = "",
    val species: String = "",
    val note: String = "",
    val distanceM: Double,
    val warnings: Set<Warning>,
    @StringRes val error: Int? = null,
)

class MeasureViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = TreeDatabase.get(app).dao()
    private val deviceId = DeviceId.get(app)
    private val locationTracker = LocationTracker(app)
    private val compass = CompassSensor(app)
    private val session = MeasureSession()

    var step by mutableStateOf(Step.MARK_BASE)
        private set
    /** String resource of the last problem to show the user, or null. */
    var hint by mutableStateOf<Int?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var form by mutableStateOf<ResultForm?>(null)
        private set
    var fix by mutableStateOf<LocationFix?>(null)
        private set

    /** Trees saved in the current run, not yet moved to the upload list. */
    val sessionRows: StateFlow<List<TreeMeasurement>> =
        dao.observeSession().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var magneticHeading: Double? = null
    // Where the user stood and which way the camera faced when marking the base.
    private var observerFix: LocationFix? = null
    private var observerHeadingDeg: Double? = null

    /** Collect while the screen is visible; cancelling stops GPS. */
    suspend fun trackLocation() = locationTracker.updates().collect { fix = it }

    suspend fun trackCompass() = compass.magneticHeading().collect { magneticHeading = it }

    /** Marks the current step at the crosshair (screen centre). */
    fun markAtCrosshair(ar: ArController) {
        if (busy || step == Step.DONE) return
        busy = true
        hint = null
        viewModelScope.launch {
            try {
                if (session.step == Step.MARK_BASE) markBase(ar) else markRay(ar)
            } finally {
                busy = false
                syncStep()
            }
        }
    }

    private suspend fun markBase(ar: ArController) {
        val points = withTimeoutOrNull(FRAME_TIMEOUT_MS) {
            ar.withFrame { frame, w, h ->
                ArFrameAdapter.cameraPosition(frame) to ArFrameAdapter.surfacePoint(frame, w / 2f, h / 2f)
            }
        }
        val camera = points?.first
        val base = points?.second
        hint = when {
            camera == null -> R.string.hint_not_tracking
            base == null -> R.string.hint_no_ground
            !session.markBase(camera, base) -> R.string.hint_too_close
            else -> {
                observerFix = fix
                observerHeadingDeg = currentTrueHeading()
                null
            }
        }
    }

    private suspend fun markRay(ar: ArController) {
        val ray = withTimeoutOrNull(FRAME_TIMEOUT_MS) {
            ar.withFrame { frame, w, h -> ArFrameAdapter.rayThrough(frame, w / 2f, h / 2f, w, h) }
        }
        if (ray == null) {
            hint = R.string.hint_not_tracking
            return
        }
        hint = when (session.step) {
            Step.MARK_TOP -> if (session.markTop(ray)) null else R.string.hint_bad_top
            Step.MARK_TRUNK_LEFT -> if (session.markTrunkLeft(ray)) null else R.string.hint_bad_edges
            Step.MARK_TRUNK_RIGHT -> if (session.markTrunkRight(ray)) null else R.string.hint_bad_edges
            else -> null
        }
    }

    fun skipDiameter() {
        session.skipDiameter()
        syncStep()
    }

    fun redo() {
        session.reset()
        form = null
        hint = null
        observerFix = null
        observerHeadingDeg = null
        syncStep()
    }

    fun updateForm(change: (ResultForm) -> ResultForm) {
        form = form?.let { change(it).copy(error = null) }
    }

    fun saveAndNext() {
        val f = form ?: return
        val treeId = TreeId.normalize(f.treeId)
        if (treeId != null && !TreeId.isValid(treeId)) {
            form = f.copy(error = R.string.err_tree_id)
            return
        }
        val species = f.species.trim()
        if (species.length > 100) {
            form = f.copy(error = R.string.err_species_long)
            return
        }
        val height = parseOptional(f.height)
        val diameter = parseOptional(f.diameter)
        if (height == INVALID || diameter == INVALID) {
            form = f.copy(error = R.string.err_number)
            return
        }

        val obs = observerFix
        val bearing = observerHeadingDeg
        // Prefer the tree's own position; fall back to where the user stood when the compass is missing.
        val position = obs?.let { o ->
            val from = LatLon(o.latitude, o.longitude)
            if (bearing != null) GeoMath.destination(from, bearing, f.distanceM) else from
        }
        val row = TreeMeasurement(
            treeId = treeId,
            species = species.ifEmpty { null },
            heightM = height,
            diameterCm = diameter,
            distanceToTreeM = f.distanceM,
            latitude = position?.latitude,
            longitude = position?.longitude,
            altitudeM = obs?.altitudeM,
            gpsAccuracyM = obs?.accuracyM,
            bearingDeg = if (obs != null) bearing else null,
            note = f.note.trim(),
            deviceId = deviceId,
            inSession = true,
        )
        viewModelScope.launch {
            dao.insert(row)
            redo()
        }
    }

    /** Moves this run's trees to the upload list. */
    fun finishSession(onDone: () -> Unit) {
        viewModelScope.launch {
            dao.finishSession()
            redo()
            onDone()
        }
    }

    private fun syncStep() {
        step = session.step
        val result = session.result
        if (step == Step.DONE && form == null && result != null) {
            form = ResultForm(
                height = result.heightM?.let { format(it, 2) }.orEmpty(),
                diameter = result.diameterCm?.let { format(it, 1) }.orEmpty(),
                distanceM = result.distanceToTreeM,
                warnings = result.warnings,
            )
        }
    }

    private fun currentTrueHeading(): Double? {
        val magnetic = magneticHeading ?: return null
        val f = fix ?: return null
        return trueHeadingDeg(magnetic, CompassSensor.declination(f.latitude, f.longitude, f.altitudeM ?: 0.0))
    }

    private companion object {
        const val FRAME_TIMEOUT_MS = 2_000L
        const val INVALID = -1.0

        fun format(v: Double, decimals: Int) = String.format(Locale.US, "%.${decimals}f", v)

        /** Blank -> null, a non-negative number -> it, anything else -> [INVALID]. */
        fun parseOptional(text: String): Double? {
            val t = text.trim()
            if (t.isEmpty()) return null
            return t.toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() } ?: INVALID
        }
    }
}
