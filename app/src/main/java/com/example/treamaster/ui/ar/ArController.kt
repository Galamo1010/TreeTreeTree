package com.example.treamaster.ui.ar

import com.example.treamaster.measure.Mark
import com.google.ar.core.Frame
import kotlinx.coroutines.suspendCancellableCoroutine

/** Lets a ViewModel read the live ARCore frame without holding the GL view. Bind it via [ArCameraView]. */
class ArController {
    @Volatile internal var renderer: ArRenderer? = null
        set(value) {
            field = value
            value?.marks = marks
        }

    /** Marked points to show as 3D markers in the preview. Kept across preview re-binds. */
    @Volatile var marks: List<Mark> = emptyList()
        set(value) {
            field = value
            renderer?.marks = value
        }

    /**
     * Runs [block] on the GL thread with the next frame and the view size, and returns its result.
     * Returns null when no preview is bound. Suspends until a frame is drawn, so wrap it in a timeout.
     */
    suspend fun <T> withFrame(block: (frame: Frame, viewW: Int, viewH: Int) -> T): T? {
        val r = renderer ?: return null
        return suspendCancellableCoroutine { cont ->
            r.runOnNextFrame { frame, w, h -> cont.resumeWith(runCatching { block(frame, w, h) }) }
        }
    }
}
